# 부하 테스트와 JVM 힙

"서버가 몇 명까지 버티는가" 와 "힙이 왜 계속 차오르는가" 는 사실 같은 질문이다.
부하를 넣지 않으면 힙 문제는 보이지 않고, 힙을 보지 않으면 부하 테스트 결과를 해석할 수 없다.
이 모듈은 두 주제를 한 프로젝트에서 같이 다룬다.

손으로 굴리는 순서는 [STEPS.md](STEPS.md) 에, 부하 스크립트는 [loadtest/README.md](loadtest/README.md) 에 있다.
이 문서는 그 과정에서 필요한 개념과 판단 기준을 정리한 참조 문서다.

**환경**: Java 17 · Spring Boot 3.4.1 · G1GC 기본 · k6(선택)

---

## 1. JVM 메모리 구조

`-Xmx` 는 힙만 제한한다. 컨테이너가 OOMKill 되는 사고의 대부분은 힙 밖에서 일어난다.

```
프로세스 전체 메모리 (컨테이너 limit 이 보는 값)
├── 힙 (-Xmx)                             <- GC 가 관리. 우리가 보통 말하는 "메모리"
│   ├── Young: Eden + Survivor 0/1        <- 객체가 태어나는 곳
│   └── Old                               <- 살아남은 객체가 승격되는 곳
└── 힙 밖 (-Xmx 와 무관, 그러나 RSS 에는 포함)
    ├── Metaspace                         <- 클래스 메타데이터. 네이티브 메모리
    ├── Code Cache                        <- JIT 컴파일 결과
    ├── Thread Stack                      <- 스레드 1개당 기본 1MB (-Xss)
    ├── Direct Buffer / MMap              <- NIO, Netty, 파일 채널
    └── GC 자체 자료구조                     <- 힙 크기에 비례해 커진다
```

실무에서 자주 밟는 것:

* **스레드 스택**. Tomcat max-threads 200 이면 스택만 약 200MB 다. `-Xmx` 를 컨테이너 한계에 딱 맞추면 죽는다.
* **Metaspace**. 기본이 무제한이라 클래스로더 누수(동적 프록시, 반복 배포)가 나면 여기가 먼저 터진다.
* **Direct Buffer**. Netty/WebClient 를 쓰면 힙은 널널한데 프로세스가 OOMKill 된다.

> 컨테이너에서는 `힙 + 힙 밖` 이 limit 안에 들어와야 한다.
> 그래서 `-Xmx` 를 limit 의 50~75% 로 잡는다. `MaxRAMPercentage` 가 이 계산을 대신해준다.

### 세대 구조가 존재하는 이유

**약한 세대 가설(weak generational hypothesis)**: 대부분의 객체는 만들어지자마자 죽는다.

HTTP 요청 하나를 처리하며 만든 DTO, StringBuilder, 파싱된 JSON 은 응답이 나가는 순간 전부 쓰레기다.
살아남는 것은 극소수(캐시, 세션, 커넥션)다. 그래서 GC 는 힙 전체를 뒤지지 않고
새로 태어난 영역(Young)만 자주 청소한다. 이것이 Minor GC 가 싼 이유다.

```
new 객체 -> Eden -> (Minor GC 생존) -> Survivor -> ... -> (age 임계 초과) -> Old
```

이 흐름에서 알아둘 것:

* **Minor GC 비용은 살아남은 객체 수에 비례한다.** 죽은 객체가 아무리 많아도 거의 공짜다.
  그래서 "임시 객체를 많이 만드는 것" 자체는 대개 문제가 아니다.
* **Old 가 차면 Major/Full GC 가 돈다.** 이건 비싸다. 여기가 튜닝의 전장이다.
* **동시 처리 중인 요청이 붙잡은 객체는 Survivor 를 거쳐 Old 로 승격된다.**
  부하가 높으면 요청 스코프 객체마저 Old 로 올라가고(premature promotion), Old 가 빨리 찬다.
  부하 테스트에서 Full GC 가 늘어나는 전형적인 경로다.

---

## 2. GC 는 무엇을 수거하는가

**쓰지 않는 객체가 아니라, 도달할 수 없는 객체를 수거한다.**

GC Root(스레드 스택의 지역변수, static 필드, JNI 참조 등)에서 참조를 타고 갈 수 있으면 살아있는 것으로 본다.
다시는 쓰지 않을 객체라도 참조 사슬이 이어져 있으면 영원히 남는다.

메모리 누수의 정의가 여기서 나온다: **더 이상 쓰지 않는데 도달 가능한 객체.**

`ReachabilityTest` 가 이것을 코드로 확인한다. 실무 누수의 대부분은 아래 다섯 가지다.

| 누수 패턴 | 왜 도달 가능한가 | 처방 |
|---|---|---|
| 상한 없는 캐시 (`Map` 필드) | 싱글턴 빈이 참조 | 상한 + TTL. Caffeine `maximumSize`/`expireAfterWrite` |
| `static` 컬렉션 | 클래스가 살아있는 한 GC Root | static 가변 컬렉션을 쓰지 않는다 |
| ThreadLocal 미정리 | 스레드풀 스레드가 재사용되며 계속 참조 | `finally` 에서 `remove()`. 필터/인터셉터에서 특히 중요 |
| 리스너/콜백 등록 후 해제 안 함 | 이벤트 소스가 참조 | 등록한 곳에서 해제. 짧은 수명이면 Weak 참조 |
| 닫지 않은 리소스 | 커넥션/스트림이 버퍼를 붙잡음 | try-with-resources |

`SoftReference` 를 캐시 상한 대용으로 쓰는 것은 안티패턴이다.
"힙이 부족해질 때까지 유지" 가 규약이라 결국 힙을 꽉 채우고 GC 압력만 키운다.

---

## 3. GC 알고리즘 고르기

| GC | 특징 | 이럴 때 |
|---|---|---|
| Serial | 단일 스레드, 가장 단순 | 힙 100MB 이하, CPU 1개. 소형 컨테이너/배치 |
| Parallel | 처리량 최우선, pause 김 | 지연보다 총 처리량이 중요한 배치 |
| **G1** (Java 9+ 기본) | region 기반, pause 목표 설정 가능 | **웹 서비스의 기본 선택** |
| ZGC | pause 1ms 이하, 힙 크기와 무관 | 힙이 수십 GB 이고 지연이 SLO 인 서비스 |
| Shenandoah | ZGC 와 유사(OpenJDK 배포판) | 위와 동일 |

Java 17 기준 실무 판단:

* **대개 G1 이 맞다.** GC 를 바꿔 얻는 개선보다 코드와 힙 크기에서 얻는 개선이 훨씬 크다.
* ZGC 는 힙이 작으면 오히려 손해다. Java 17 의 ZGC 는 non-generational 이라
  "금방 죽는 객체" 최적화가 없다. generational ZGC 는 JDK 21 부터다.
* G1 은 `-XX:MaxGCPauseMillis`(기본 200ms) 로 목표를 준다. **목표지 보장이 아니다.**
  너무 작게 잡으면 Young 영역을 잘게 쪼개 GC 를 더 자주 돌린다(= 처리량 하락).

### G1 의 humongous 할당

G1 은 힙을 균등한 region(기본 1~32MB, 힙 크기에 따라 자동)으로 나눈다.
**region 의 절반을 넘는 객체는 humongous** 로 분류돼 연속된 region 들을 통째로 요구한다.

* 힙에 여유 총량이 있어도 **연속된 자리가 없으면 실패**한다. 이것이 단편화다.
* 대용량 파일을 통째로 `byte[]` 로 읽거나, 수만 건을 한 번에 조회해 리스트로 만드는 코드가 원인이 된다.
* 처방은 스트리밍/청크 처리다. 정말 필요하면 `-XX:G1HeapRegionSize` 를 키운다.

`POST /lab/humongous` 로 재현한다.

---

## 4. 힙 옵션

```bash
-Xms2g -Xmx2g                 # 최소=최대로 고정
-XX:MaxRAMPercentage=75.0     # 컨테이너에서는 이쪽
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dump
-Xlog:gc*:file=/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m
```

**`-Xms` 와 `-Xmx` 를 같게 잡는 이유**: 힙을 늘리고 줄이는 것 자체가 비용이다.
운영 서버는 어차피 최대치까지 쓰게 되므로 처음부터 확보한다.
그리고 힙이 커지는 과정에서 GC 동작이 계속 달라져 부하 테스트 결과가 재현되지 않는다.

**컨테이너**: `-Xmx` 를 하드코딩하면 limit 을 바꿀 때마다 이미지를 다시 만들어야 한다.
`MaxRAMPercentage` 는 cgroup limit 을 읽어 비례 계산한다 (JDK 10+ 컨테이너 인식).

```bash
# limit 1Gi, MaxRAMPercentage=75 -> 힙 768MB, 나머지 256MB 가 스택/메타스페이스/네이티브 몫
java -XX:MaxRAMPercentage=75.0 -jar app.jar
```

**힙을 키우면 다 해결되나**: 아니다.

* 누수는 힙을 키우면 **터지는 시점만 미뤄진다**. 대신 힙덤프가 커져 분석이 더 어려워진다.
* 힙이 크면 Full GC 한 번의 pause 가 길어진다. p99 가 더 나빠질 수 있다.
* 진짜로 데이터가 많아서 부족한 경우에만 키우는 것이 맞다. 그 구분을 부하 테스트로 한다.

---

## 5. OutOfMemoryError 읽기

| 메시지 | 실제 의미 | 먼저 볼 것 |
|---|---|---|
| `Java heap space` | 힙 부족 | 누수인지 실제 부족인지. 힙덤프 dominator tree |
| `GC overhead limit exceeded` | GC 에 98% 시간을 쓰고 2% 미만 회수 | 사실상 누수 확정. 힙덤프 |
| `Metaspace` | 클래스 메타데이터 부족 | 클래스로더 누수, 동적 프록시 남발 |
| `unable to create new native thread` | OS 스레드 한계 | 스레드 수, 스레드풀 설정, `ulimit -u` |
| `Direct buffer memory` | 네이티브 버퍼 부족 | Netty/NIO, `-XX:MaxDirectMemorySize` |
| `Requested array size exceeds VM limit` | 배열 크기가 Integer 한계 근처 | 한 방에 다 읽는 코드 |

**컨테이너에서 OOM 이면 두 종류를 구분해야 한다.**

* JVM 이 던진 `OutOfMemoryError` -> 스택트레이스가 남는다. 힙 문제.
* 커널의 OOMKill (exit code 137) -> **로그가 없다.** 힙 밖 메모리까지 합쳐 limit 을 넘은 것이다.
  `kubectl describe pod` 의 `Reason: OOMKilled` 로만 확인된다.

---

## 6. 관측 도구

### 6.1 GC 로그 (가장 먼저 켤 것)

```bash
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=10m
```

```
[2.455s][info][gc] GC(3) Pause Young (Normal) (G1 Evacuation Pause) 108M->12M(256M) 5.234ms
                       ^세대      ^원인                              ^전 ^후 ^전체힙   ^pause
```

읽는 법:

* **화살표 뒤의 숫자(GC 후 사용량)가 저점이다.** 이 저점이 계속 올라가면 누수다. 톱니의 높이는 상관없다.
* `Pause Full` 이 반복해서 나오면 이미 위험하다.
* pause 합계가 전체 시간의 5% 를 넘으면 튜닝 대상이다.

### 6.2 명령줄

```bash
jcmd <pid> GC.heap_info          # 세대별 사용량 스냅샷
jcmd <pid> GC.class_histogram    # 클래스별 인스턴스 수/바이트 (누수 용의자 찾기)
jcmd <pid> Thread.print          # 스레드 덤프. 스레드 고갈 분석
jcmd <pid> GC.heap_dump C:/tmp/heap.hprof   # 힙덤프 (STW 발생. 운영에서는 주의)
jstat -gcutil <pid> 1000         # 1초마다 세대별 사용률과 GC 횟수/시간
```

`GC.class_histogram` 을 **부하 전후로 두 번 찍어 비교**하는 것이 실무에서 가장 빠른 누수 탐지법이다.

### 6.3 힙덤프 분석

Eclipse MAT 로 연다. 순서는 항상 같다.

1. **Leak Suspects** 리포트 확인
2. **Dominator Tree** 에서 retained heap 이 큰 객체 찾기
   (shallow heap = 자기 크기, **retained heap = 이 객체가 죽으면 같이 사라질 총량**)
3. 용의자에서 **Path to GC Roots - exclude weak/soft references** 로 누가 붙잡고 있는지 확인

3번이 핵심이다. "무엇이 큰가" 가 아니라 **"누가 놓아주지 않는가"** 를 찾는 것이다.

### 6.4 애플리케이션 지표 (이 모듈)

```bash
curl localhost:8080/lab/heap                            # 힙/GC 요약 (사람이 읽는 용)
curl localhost:8080/actuator/metrics/jvm.memory.used    # 표준 지표
curl localhost:8080/actuator/metrics/jvm.gc.pause
curl localhost:8080/actuator/prometheus | grep jvm_     # Prometheus 스크랩 형식
```

운영 대시보드에 반드시 올릴 것:

| 지표 | 경보 기준 |
|---|---|
| `jvm_memory_used_bytes{area="heap"}` | GC 후 저점의 우상향 추세 |
| `jvm_gc_pause_seconds` | p99, 그리고 시간당 pause 총합 |
| `jvm_threads_live_threads` | 최대치 근접 |
| `tomcat_threads_busy_threads` / `max` | 80% 초과 지속 |
| `hikaricp_connections_pending` | 0 이 아니면 이미 병목 |

> `/actuator/heapdump` 는 힙 전체를 내려준다. 그 안에는 토큰, 비밀번호, 개인정보가 그대로 들어있다.
> **운영에서 인증 없이 열어두면 사고다.**

---

## 7. 부하 테스트

### 7.1 무엇을 재는가

| 지표 | 의미 | 함정 |
|---|---|---|
| TPS/RPS | 처리량 | 에러가 늘면 같이 오른다. 에러율과 반드시 같이 본다 |
| p50 | 절반의 사용자 경험 | 평상시 상태 |
| **p95 / p99** | **SLO 로 쓰는 값** | 여기가 튀는 원인은 대개 GC 나 큐 대기 |
| max | 최악의 사용자 | 한 건이라도 있으면 원인은 있다 |
| error rate | 실패 비율 | **에러가 나면 지연 통계는 무의미해진다** |

**평균은 보지 않는다.** 99건이 10ms, 1건이 2초면 평균은 30ms 다. 아무 문제 없어 보인다.
`LoadResultMathTest` 가 이 계산을 그대로 검증한다.

에러가 난 뒤의 지연 숫자를 믿으면 안 되는 이유: 실패한 요청은 **빨리** 끝난다.
서버가 무너질수록 p95 가 오히려 좋아 보이는 착시가 생긴다. 그래서 에러율을 먼저 본다.

### 7.2 시나리오 종류

| 종류 | 목적 | 무엇을 잡는가 |
|---|---|---|
| smoke | 스크립트/환경 점검 | 배포 직후 기본 동작 |
| load | 평상시 트래픽 재현 | SLO 위반 여부 |
| stress | 한계점 탐색 | saturation point, 붕괴 양상 |
| spike | 순간 급증 | 버티는가 + 부하가 빠진 뒤 **회복하는가** |
| **soak** | 낮은 부하를 오래 | **메모리/커넥션 누수** |

**힙 문제는 soak 로만 잡힌다.** 5분짜리 부하 테스트에서 멀쩡한 서비스가
3일 뒤 새벽에 죽는 이유가 이것이다.

### 7.3 리틀의 법칙

```
필요한 동시 처리 수 = 도착률(TPS) x 평균 응답시간(초)
```

200 TPS 를 응답 0.5초로 처리하려면 동시에 100건이 떠 있어야 한다.
Tomcat 스레드가 20개면 80건은 큐에서 기다리고, 사용자가 겪는 시간은 `처리시간 + 대기시간` 이 된다.

이 법칙이 힙과 만나는 지점:

```
상주 메모리 = 동시 처리 수 x 요청당 메모리
            = (TPS x 응답시간) x 요청당 메모리
```

요청당 512KB 를 쓰는 API 를 동시 100건 처리하면 그것만으로 50MB 가 힙에 상주한다.
**TPS 목표를 2배로 올리면 필요한 힙도 2배가 된다.**
부하 테스트 없이 `-Xmx` 를 정하는 것이 위험한 이유가 이것이다.

응답시간을 0.5초에서 0.05초로 줄이면 같은 TPS 를 10개 스레드로 감당하고, 상주 메모리도 1/10 이 된다.
**튜닝의 우선순위가 항상 "응답시간 단축" 인 이유**다.

### 7.4 부하 테스트를 망치는 것들

* **부하 생성기와 서버가 같은 머신에 있다.** CPU 를 서로 뺏는다. (이 모듈의 `loadTest` 가 딱 이 상태다)
* **워밍업이 없다.** JVM 은 JIT 컴파일 전 인터프리터로 돌아 10배 이상 느리다.
  워밍업 없이 잰 p99 는 예열 비용을 성능 문제로 오해하게 만든다.
* **coordinated omission.** 서버가 느려지면 부하 도구가 요청을 덜 보낸다.
  실제 사용자가 겪는 지연보다 좋게 측정된다. k6 의 `constant-arrival-rate` 실행자로 완화한다.
* **캐시가 데워진 상태만 측정.** 같은 키만 조회하면 실제 트래픽과 전혀 다른 결과가 나온다.
* **한 번에 여러 개를 바꾼다.** 힙 크기와 GC 를 동시에 바꾸면 무엇이 효과였는지 알 수 없다.

---

## 8. 두 주제가 만나는 곳

부하를 넣었을 때 힙 그래프가 그리는 모양은 셋 중 하나다.

```
(1) 건강함             (2) 부하 비례            (3) 누수
 /|/|/|/|/|            /‾‾‾‾\                  /|/|/|/
/ | | | | |           /      \                / | | |
저점 평평              부하 끝나면 회복          저점이 우상향
```

* **(1) 톱니 모양, 저점 평평** - 정상. GC 가 자주 돌아도 문제없다. 임시 객체가 많을 뿐이다.
* **(2) 부하 중 상승, 부하가 끝나면 회복** - 정상. 동시 처리 중인 요청이 붙잡은 메모리다.
  다만 그 최고점이 `-Xmx` 에 가까우면 용량 산정을 다시 해야 한다.
* **(3) 저점이 계속 올라감** - 누수. GC 를 강제로 돌려도 안 내려온다.

판단 기준은 언제나 **"GC 직후의 저점"** 하나다. 톱니의 높이와 GC 횟수는 판단 근거가 아니다.

부하 테스트에서 p99 가 튈 때의 감별:

| 관찰 | 원인 | 확인 방법 |
|---|---|---|
| p99 가 튀는 시점 = GC pause 시점 | GC | GC 로그 타임스탬프 대조 |
| p50 도 같이 크게 오름, CPU 는 한가함 | 스레드/커넥션 큐 대기 | `tomcat_threads_busy`, `hikaricp_connections_pending` |
| CPU 100% | 연산 병목 | 프로파일러(async-profiler, JFR) |
| 시간이 갈수록 계속 나빠짐 | 누수 | 힙 저점 추세 |

---

## 9. 튜닝 절차

1. **기준선을 남긴다.** 고치기 전 숫자가 없으면 개선을 증명할 수 없다.
2. **가설을 하나 세운다.** "Old 가 차서 Full GC 가 잦다" 같은 형태로.
3. **하나만 바꾼다.** 힙 크기 -> 재측정 -> GC 옵션 -> 재측정.
4. **코드를 먼저 본다.** 대부분의 힙 문제는 옵션이 아니라 코드가 원인이다.
   상한 없는 캐시, 한 번에 다 읽는 쿼리, 응답 DTO 에 엔티티 통째로 담기.
5. **soak 로 확인한다.** 짧은 테스트는 누수를 못 잡는다.

옵션으로 해결되는 경우는 생각보다 드물다. 순서를 지키는 것이 요령이다.

---

## 10. 이 모듈 사용법

### 실행

```bash
# 기본(256m, G1)
./gradlew :jvm-heap-loadtest:bootRun

# 프리셋: tiny(64m) / serial / parallel / zgc / container
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=tiny

# 직접 지정
./gradlew :jvm-heap-loadtest:bootRun -Pjvm="-Xmx128m -XX:+UseG1GC"
```

GC 로그는 `jvm-heap-loadtest/build/gc-<preset>.log`,
힙덤프는 `build/heapdump-<preset>.hprof` 로 떨어진다.

### 엔드포인트

| 엔드포인트 | 하는 일 | 보는 것 |
|---|---|---|
| `POST /api/orders?items=20` | 정상 워크로드 | 기준선. 힙이 톱니로 오르내린다 |
| `GET /lab/heap` | 힙/GC 스냅샷 | 세대별 사용량 |
| `POST /lab/leak?count=200&payloadKb=50` | 누수 유발 | 저점이 우상향 |
| `DELETE /lab/leak` | 참조 해제 | GC 후 회수되는 것 |
| `POST /lab/alloc?totalMb=100` | 쓰레기 대량 생성 | GC 는 폭증, 힙은 제자리 |
| `POST /lab/humongous?sizeMb=32` | 거대 객체 한 방 | 총량이 남아도 실패하는 단편화 |
| `GET /lab/blocking?millis=300&bufferKb=512` | 느린 응답 | 스레드 고갈, 동시성 x 요청당 메모리 |

### 테스트

```bash
./gradlew :jvm-heap-loadtest:test        # 힙/참조/캐시/지표 계산 단위 테스트
./gradlew :jvm-heap-loadtest:loadTest    # 내장 부하 생성기 시나리오 4종
```

| 테스트 | 확인하는 것 |
|---|---|
| `ReachabilityTest` | 도달 가능성이 수거를 결정한다. Weak/Soft/WeakHashMap |
| `HeapGrowthTest` | 쓰레기 vs 누수, 동시성 x 요청당 메모리 |
| `CacheBoundaryTest` | 상한 없는 캐시는 누수, LRU 는 평평 |
| `LoadResultMathTest` | 평균의 거짓말, 백분위수, 리틀의 법칙 |
| `LoadScenarioTest` | 실제 HTTP 부하 4종 (`@Tag("load")`) |

---

## 11. 증상 - 대응 인덱스

| 증상 | 먼저 확인 | 흔한 원인 |
|---|---|---|
| 힙 저점이 우상향 | 힙덤프 dominator tree | 상한 없는 캐시, ThreadLocal, static 컬렉션 |
| Full GC 반복 + 회수 거의 없음 | `GC overhead limit exceeded` 여부 | 누수 확정 |
| p99 만 튄다 | GC 로그 pause 와 시각 대조 | Old 승격 과다, 힙 부족 |
| p50 부터 무너진다, CPU 한가 | 스레드/커넥션 풀 지표 | 스레드 고갈, 느린 외부 호출 |
| 컨테이너 exit 137, 로그 없음 | `kubectl describe pod` | 힙 밖 메모리(스레드 스택, direct buffer, metaspace) |
| 힙은 널널한데 OOM | OOM 메시지 종류 | Metaspace, Direct buffer, humongous 할당 |
| 부하 끝나도 메모리 안 내려옴 | GC 강제 후 재확인 | 누수 또는 커밋된 힙(정상) |
| 재시작하면 며칠 뒤 재발 | soak 테스트 | 누수 |

---

## 12. 실무 체크리스트

**배포 전**

- [ ] `-Xms` = `-Xmx` (또는 컨테이너에서 `MaxRAMPercentage`)
- [ ] `-XX:+HeapDumpOnOutOfMemoryError` + 덤프 경로가 **영속 볼륨**을 향하는가
- [ ] GC 로그가 파일로 남고 rotate 되는가
- [ ] 컨테이너 limit 이 `힙 + 스레드 스택 + 메타스페이스 + 네이티브` 를 감당하는가
- [ ] `/actuator/heapdump`, `/actuator/env` 가 인증 없이 열려 있지 않은가

**코드**

- [ ] 모든 캐시에 상한과 만료가 있는가
- [ ] ThreadLocal 을 `finally` 에서 `remove()` 하는가
- [ ] 대용량 조회에 페이징/스트리밍이 있는가 (`List` 로 다 받지 않는가)
- [ ] 파일을 통째로 `byte[]` 로 읽는 곳이 없는가

**부하 테스트**

- [ ] 기준선 숫자를 저장해뒀는가
- [ ] 워밍업 구간을 통계에서 제외했는가
- [ ] 에러율을 지연보다 먼저 보는가
- [ ] p95/p99 를 threshold 로 CI 에 걸었는가
- [ ] 누수 확인용 soak 시나리오가 있는가
- [ ] 부하 중 서버 쪽 지표(GC, 힙, 스레드, 커넥션)를 같이 수집했는가
