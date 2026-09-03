# 실습 단계

각 단계는 **"뭘 한다 -> 뭘 관찰한다 -> 왜 그런가"** 순서다.
개념 정리와 증상별 대응 인덱스는 [LEARN.md](LEARN.md) 에 있다. 이 문서는 손으로 굴리는 순서다.

**핵심 질문은 처음부터 끝까지 하나다: GC 직후의 저점이 올라가고 있는가?**

---

## 0. 준비

명령은 저장소 루트에서 실행한다.

```bash
./gradlew :jvm-heap-loadtest:bootRun
```

다른 터미널에서 상태를 본다.

```bash
# Git Bash / macOS / Linux
curl -s localhost:8080/lab/heap

# PowerShell (curl 은 Invoke-WebRequest 별칭이라 아래를 쓴다)
Invoke-RestMethod localhost:8080/lab/heap | ConvertTo-Json -Depth 5
```

프로세스 ID 를 찾아둔다. 이후 `jcmd` 에 계속 쓴다.

```bash
jps -l | grep jvm-heap
jcmd <pid> GC.heap_info
```

**관찰**: `max` 가 256MB, 풀 이름이 `G1 Eden Space` / `G1 Old Gen` 이다.
힙 풀 이름만 봐도 어떤 GC 가 도는지 알 수 있다.

---

## 1. 정상 워크로드의 힙 모양 (기준선)

**한다**: 정상 엔드포인트에 부하를 넣는다.

```bash
# 내장 생성기
./gradlew :jvm-heap-loadtest:loadTest --tests '*LoadScenarioTest.baseline*'

# 또는 k6
k6 run loadtest/k6/load.js
```

부하가 도는 동안 다른 터미널에서 힙을 반복 조회한다.

```bash
for i in $(seq 30); do curl -s localhost:8080/lab/heap | head -c 120; echo; sleep 2; done
```

**관찰**:
* `used` 가 올랐다 내렸다 한다(톱니).
* 부하가 끝나고 잠시 뒤 원래 수준으로 돌아온다.
* GC 는 계속 돌지만 `Old Gen` 은 거의 늘지 않는다.

**왜**: 요청당 만든 객체는 응답과 함께 죽는다. Eden 에서 태어나 첫 Minor GC 에 사라진다.
Minor GC 비용은 **살아남은 객체 수**에 비례하므로, 죽는 객체가 많은 것은 거의 공짜다.

리포트의 숫자를 적어둔다. 이후 모든 비교의 기준선이다.

```
baseline: TPS ___ / p95 ___ ms / p99 ___ ms / GC pause ___ ms
```

---

## 2. 쓰레기를 많이 만드는 것은 문제가 아니다

**한다**: 힙 크기의 몇 배에 해당하는 쓰레기를 한 번에 만든다.

```bash
curl -s -X POST "localhost:8080/lab/alloc?totalMb=500&chunkKb=16"
```

**관찰** (실제 실행 결과):

```json
{"allocated":"500.0MB","elapsedMs":42,"gcCollections":3,"gcPauseMs":4,
 "gcPauseRatio":"9.5%","heapUsedBefore":"59.6MB","heapUsedAfter":"155.7MB"}
```

호출 직후에는 힙이 올라가 있다. 아직 Eden 에 쓰레기가 그대로 쌓여 있기 때문이다.
**중요한 것은 그 다음 GC 가 무엇을 회수했는가**다. GC 로그를 본다.

```bash
grep "Pause Young" jvm-heap-loadtest/build/gc-default.log | tail -3
```

```
GC(15) Pause Young (Normal) (G1 Evacuation Pause) 184M->52M(256M) 1.359ms
GC(16) Pause Young (Normal) (G1 Evacuation Pause) 189M->52M(256M) 1.034ms
GC(17) Pause Young (Normal) (G1 Evacuation Pause) 195M->52M(256M) 1.102ms
```

* 500MB 를 할당했는데 GC 는 3번, pause 는 총 4ms 다.
* 매번 약 140MB 를 1ms 남짓에 회수한다.
* **화살표 뒤 숫자가 계속 52M 로 같다.** 저점이 평평하다 = 아무것도 새지 않는다.

**왜**: 만들자마자 도달 불가능해졌기 때문이다. Minor GC 비용은 **살아남은 객체**에 비례하므로,
죽은 객체가 140MB 든 1GB 든 비용은 거의 같다.

> **"GC 가 자주 돈다" 는 그 자체로 문제가 아니다.**
> 모니터링에서 GC 횟수만 보고 경보를 거는 것은 오경보 공장이 된다.
> 봐야 할 것은 횟수가 아니라 **pause 총합**과 **GC 후 저점**이다.

---

## 3. 누수 만들기

**한다**: 참조가 남는 객체를 쌓는다.

```bash
curl -s -X POST "localhost:8080/lab/leak?count=200&payloadKb=50"   # 약 10MB
# 5번 반복
curl -s localhost:8080/lab/heap
```

강제로 GC 를 돌려도 안 내려간다는 것까지 확인한다.

```bash
jcmd <pid> GC.run
curl -s localhost:8080/lab/heap
```

**관찰**: `Old Gen` 이 계단식으로 올라가고, GC 를 돌려도 내려오지 않는다.

**한다**: 참조를 끊는다.

```bash
curl -s -X DELETE localhost:8080/lab/leak
jcmd <pid> GC.run
curl -s localhost:8080/lab/heap
```

**관찰**: 이제 내려온다.

**왜**: GC 는 쓰지 않는 객체가 아니라 **도달할 수 없는** 객체를 수거한다.
싱글턴 빈이 Map 을 들고 있으면 그 안의 모든 것은 GC Root 에서 도달 가능하다.
"조회 성능을 위해 Map 에 담아뒀다" 로 시작하는 코드가 운영에서 OOM 을 내는 이유가 정확히 이것이다.

**같이 볼 것**: 누가 붙잡고 있는지 클래스 히스토그램으로 확인한다.

```bash
jcmd <pid> GC.class_histogram | head -20
```

`byte[]` 인스턴스 수가 요청 횟수에 비례해 늘어나 있다. 부하 전후로 두 번 찍어 비교하는 것이
실무에서 가장 빠른 누수 탐지법이다.

---

## 4. 진짜로 OOM 을 내고 힙덤프 분석하기

**한다**: 힙을 64MB 로 줄여 띄운 뒤 누수를 반복 호출한다.

```bash
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=tiny
```

```bash
for i in $(seq 20); do curl -s -X POST "localhost:8080/lab/leak?count=100&payloadKb=50" > /dev/null; echo $i; done
```

**관찰**:

```
java.lang.OutOfMemoryError: Java heap space
```

`jvm-heap-loadtest/build/heapdump-tiny.hprof` 파일이 생겼다.
`-XX:+HeapDumpOnOutOfMemoryError` 덕분이다. **이 옵션 없이 OOM 이 나면 원인을 알 방법이 거의 없다.**
운영 배포에 반드시 넣어야 하는 이유다.

**한다**: Eclipse MAT 로 덤프를 연다. ([다운로드](https://eclipse.dev/mat/))

1. Leak Suspects 리포트를 본다
2. Dominator Tree 에서 retained heap 이 큰 것을 찾는다
3. 그 객체에서 **Path to GC Roots - exclude weak/soft references**

**관찰**: `LeakySessionStore` -> `ConcurrentHashMap` -> `byte[]` 사슬이 보인다.
`LabConfig` 가 만든 싱글턴 빈이 GC Root 다.

**왜**: shallow heap(자기 크기)이 아니라 **retained heap(이 객체가 죽으면 같이 사라질 총량)** 을 봐야
"누가 붙잡고 있는가" 가 나온다. 큰 것을 찾는 게 아니라 **놓아주지 않는 것**을 찾는 작업이다.

> OOM 이 난 상태에서 앱이 계속 떠 있으면 더 위험하다. 응답은 실패하는데 헬스체크는 통과할 수 있다.
> 운영에서는 `-XX:+ExitOnOutOfMemoryError` 로 즉시 죽이고 재시작시키는 편이 낫다.

---

## 5. GC 로그 읽기

**한다**: 앞 단계에서 남은 로그를 연다.

```bash
tail -40 jvm-heap-loadtest/build/gc-tiny.log
grep "Pause Full" jvm-heap-loadtest/build/gc-tiny.log | tail -20
```

**관찰**:

```
[12.345s][info][gc] GC(41) Pause Young (Normal) (G1 Evacuation Pause) 62M->58M(64M) 12.3ms
[12.501s][info][gc] GC(42) Pause Full (Allocation Failure) 63M->62M(64M) 210.7ms
```

두 가지를 본다.

1. **화살표 뒤 숫자(GC 후 사용량)의 추세.** 58M, 62M... 계속 올라간다 -> 누수.
2. **`Pause Full` 의 빈도.** Full GC 가 반복되는데 회수량이 적으면 사실상 사망 선고다.

정상 상태(1단계)의 로그와 비교한다.

```bash
grep "Pause Young" jvm-heap-loadtest/build/gc-default.log | tail -10
```

`108M->12M(256M)` 처럼 **뒤 숫자가 낮게 유지**되면 건강한 것이다.

---

## 6. 스레드 고갈 - 리틀의 법칙 확인

이 모듈의 Tomcat 스레드는 20개다 (`application.yml`).

**한다**: 처리에 300ms 가 걸리는 엔드포인트를 동시 40건으로 때린다.

```bash
./gradlew :jvm-heap-loadtest:loadTest --tests '*LoadScenarioTest.saturation*'
```

**관찰** (실제 실행 결과 예시):

```
requests   : 360 (error=0)
throughput : 65.0 req/s
latency    : mean=596.4 p50=613.1 p95=621.5 p99=890.0 max=904.9
```

* 서버가 쓰는 시간은 300ms 인데 응답은 약 600ms 다.
* **차이 300ms 가 큐에서 기다린 시간**이다.
* 이때 CPU 는 한가하다. 병목은 연산이 아니라 스레드 수다.

**계산해본다**: 리틀의 법칙.

```
동시 40건 / 스레드 20개 = 요청 하나가 평균 2배의 시간을 기다린다
처리량 한계 = 스레드 20개 / 0.3초 = 약 66 TPS   (실측 65.0 과 일치)
```

**왜**: 스레드가 블로킹 대기에 묶여 있으면 스레드 수가 그대로 처리량 상한이 된다.
이 병목의 해법은 CPU 증설이 아니라 **응답시간 단축**(또는 논블로킹/가상 스레드)이다.
같은 저장소의 `virtual-threads`, `threadpool` 모듈과 이어지는 지점이다.

**같이 본다**: 부하 중 스레드 덤프.

```bash
jcmd <pid> Thread.print | grep -A3 "http-nio" | head -40
```

`http-nio-8080-exec-*` 스레드들이 전부 `TIMED_WAITING` 이다.

---

## 7. 부하가 힙을 밀어올린다

**한다**: 6단계 부하 중에 힙을 본다. 요청 하나가 512KB 를 붙잡는다.

```bash
curl -s "localhost:8080/lab/blocking?millis=3000&bufferKb=1024" &   # 여러 개 동시에
curl -s localhost:8080/lab/heap
```

**관찰**: 응답의 `estimatedRetained` 와 힙 `used` 증가가 함께 움직인다.
6단계 리포트에서도 힙이 63.6MB -> 111.5MB 로 올랐다(동시 20건 x 512KB + 여유분).

**왜**:

```
상주 메모리 = 동시 처리 수 x 요청당 메모리 = (TPS x 응답시간) x 요청당 메모리
```

**TPS 목표를 2배로 잡으면 필요한 힙도 2배가 된다.**
부하 테스트 없이 `-Xmx` 를 정하면 안 되는 이유가 이것이다.
반대로 응답시간을 1/10 로 줄이면 필요한 스레드도 힙도 1/10 이 된다.
튜닝의 첫 순위가 언제나 응답시간인 이유다.

---

## 8. GC 알고리즘 바꿔서 비교하기

**한다**: 같은 부하를 GC 만 바꿔가며 돌린다. **한 번에 하나만 바꾼다.**

```bash
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=serial     # 64m + SerialGC
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=parallel   # 512m + ParallelGC
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=zgc        # 512m + ZGC
```

각각에 대해 같은 부하를 넣고 표를 채운다.

```bash
k6 run loadtest/k6/load.js
```

| 프리셋 | TPS | p95 | p99 | GC 횟수 | pause 합 | 최대 pause |
|---|---|---|---|---|---|---|
| default (G1 256m) | | | | | | |
| serial (64m) | | | | | | |
| parallel (512m) | | | | | | |
| zgc (512m) | | | | | | |

**관찰**:
* Serial 은 pause 하나가 길다. p99 가 눈에 띄게 나쁘다.
* Parallel 은 총 처리량은 좋은데 pause 가 길다.
* ZGC 는 pause 가 짧지만, 이 정도 힙 크기에서는 G1 대비 이점이 없거나 오히려 손해다.

**왜**: Java 17 의 ZGC 는 non-generational 이라 "금방 죽는 객체" 최적화가 없다.
generational ZGC 는 JDK 21 부터다. **힙이 수십 GB 가 아니면 G1 을 벗어날 이유가 거의 없다.**

GC 를 바꿔서 얻는 개선은 대개 코드 수정으로 얻는 개선보다 훨씬 작다. 이 표를 직접 채워보면 체감된다.

---

## 9. 힙 크기가 성능에 미치는 영향

**한다**: G1 을 고정하고 힙만 바꾼다.

```bash
./gradlew :jvm-heap-loadtest:bootRun -Pjvm="-Xms64m -Xmx64m -XX:+UseG1GC"
./gradlew :jvm-heap-loadtest:bootRun -Pjvm="-Xms256m -Xmx256m -XX:+UseG1GC"
./gradlew :jvm-heap-loadtest:bootRun -Pjvm="-Xms1g -Xmx1g -XX:+UseG1GC"
```

**관찰**:
* 힙이 작으면 GC 가 잦다. pause 하나는 짧지만 총합이 커진다.
* 힙이 크면 GC 는 뜸하지만 한 번의 Full GC pause 가 길어진다.
* 어느 지점 이상부터는 힙을 키워도 TPS 가 늘지 않는다.

**왜**: 힙 크기는 "GC 빈도"와 "pause 길이"의 트레이드오프다.
지연이 중요한 서비스는 무작정 키우면 오히려 p99 가 나빠진다.
그리고 **누수 앞에서는 어느 크기도 답이 아니다.** 터지는 시점만 미룰 뿐이다.

---

## 10. 힙에 자리는 있는데 실패하는 경우 (humongous)

**한다**: 256MB 힙에서 큰 배열 하나를 요청한다.

```bash
curl -s -X POST "localhost:8080/lab/humongous?sizeMb=16"
curl -s -X POST "localhost:8080/lab/humongous?sizeMb=200"
```

먼저 `/lab/leak` 으로 힙을 절반쯤 채워 조각낸 뒤 다시 시도해본다.

**관찰**: 16MB 는 성공하고, 200MB 는 `OutOfMemoryError: Java heap space` 로 실패한다.
(엔드포인트가 OOM 을 잡아 응답으로 돌려주므로 앱은 죽지 않는다)

GC 로그에서 region 크기와 humongous 할당을 확인한다.

```bash
grep -i "Heap Region Size" jvm-heap-loadtest/build/gc-default.log
grep "Humongous regions" jvm-heap-loadtest/build/gc-default.log | tail -3
```

```
Heap Region Size: 1M
GC(15) Humongous regions: 4->2
```

**왜**: G1 은 힙을 region 으로 나눈다(여기서는 1MB. 힙 크기에 따라 자동 결정).
**region 절반을 넘는 객체(여기서는 512KB 초과)는 humongous** 로 분류돼
**연속된 region** 을 통째로 요구한다. 총량이 남아도 연속된 자리가 없으면 실패한다.

이 모듈의 `/lab/blocking?bufferKb=1024` 가 만드는 1MB 버퍼도 이미 humongous 다.
요청마다 humongous 를 할당하면 GC 원인에 `G1 Humongous Allocation` 이 찍히기 시작한다.

실무 등가물: 대용량 파일을 통째로 `byte[]` 로 읽기, 수십만 건을 한 번에 `List` 로 조회하기,
큰 응답을 통째로 문자열로 만들기. 해법은 힙 증설이 아니라 **스트리밍/청크 처리**다.

---

## 11. soak - 짧은 테스트로는 절대 못 잡는 것

**한다**: 낮은 부하를 오래 건다.

```bash
# 누수 엔드포인트를 대상으로 (실습이므로 10분 정도로 줄여도 된다)
k6 run -e TARGET_PATH="/lab/leak?count=5&payloadKb=50" loadtest/k6/soak.js
```

돌리는 동안 60초마다 힙 저점을 기록한다.

```bash
while true; do
  jcmd <pid> GC.run > /dev/null
  echo "$(date +%T) $(curl -s localhost:8080/lab/heap | head -c 60)"
  sleep 60
done
```

**관찰**: TPS 와 p95 는 내내 멀쩡하다. 그런데 GC 직후 힙 저점만 계속 올라간다.
어느 순간부터 Full GC 가 잦아지고, 그때서야 지연이 무너진다.

**왜**: 누수는 **처음에는 성능에 아무 영향이 없다.** 힙이 찰 때까지는 정상으로 보인다.
그래서 5분짜리 부하 테스트를 아무리 잘 만들어도 누수는 통과한다.
"부하 테스트는 멀쩡했는데 3일 뒤 새벽에 죽었다" 의 정체가 이것이다.

**CI 에 넣을 것**: load 테스트는 매 배포마다, soak 는 주 1회 야간 파이프라인으로.

---

## 12. 컨테이너 환경

**한다**: 컨테이너 인식 옵션으로 띄운다.

```bash
./gradlew :jvm-heap-loadtest:bootRun -Pjvm=container
curl -s localhost:8080/lab/heap
```

Docker 로 확인하려면:

```bash
./gradlew :jvm-heap-loadtest:bootJar
docker run --rm -m 512m -p 8080:8080 -v "$PWD/jvm-heap-loadtest/build/libs:/app" \
  eclipse-temurin:17-jre java -XX:MaxRAMPercentage=75.0 -XX:+PrintFlagsFinal \
  -jar /app/jvm-heap-loadtest-0.0.1-SNAPSHOT.jar | grep -i maxheapsize
```

**관찰**: limit 512m 에 대해 `MaxHeapSize` 가 약 384MB(75%)로 잡힌다.

**왜**: JDK 10+ 는 cgroup limit 을 읽는다. `-Xmx` 하드코딩과 달리
limit 을 바꿔도 이미지를 다시 만들 필요가 없다.

**한다**: 나머지 25% 가 왜 필요한지 확인한다.

```bash
curl -s localhost:8080/actuator/metrics/jvm.threads.live
```

스레드 1개당 스택 1MB. Tomcat 200 스레드면 그것만 200MB 다.
`-Xmx` 를 limit 에 딱 맞추면 힙은 안 넘쳤는데 **컨테이너가 OOMKill(exit 137)** 된다.
이 경우 JVM 로그에는 아무것도 남지 않는다. `kubectl describe pod` 의 `Reason: OOMKilled` 로만 보인다.

---

## 마무리 - 관찰 기록 템플릿

성능 작업은 기록이 전부다. 매번 아래를 남긴다.

```
날짜/커밋:
JVM 옵션:
부하 시나리오: (동시성, 지속시간, 대상)
--------------------------------
TPS:            p50/p95/p99:
error rate:
GC 횟수/pause 합:      최대 pause:
힙: 시작 __ / 최고 __ / GC 후 저점 __
--------------------------------
가설:
바꾼 것 (하나만):
결과:
```

## 다음에 볼 것

| 이어지는 주제 | 모듈 |
|---|---|
| 스레드풀 튜닝과 큐 | `threadpool` |
| 블로킹 없는 처리 | `virtual-threads`, `async` |
| 지표 수집과 노출 | `actuator-deep-dive` |
| 캐시 설계와 만료 | `cache-practice`, `redis-deep-dive` |
| 컨테이너 리소스 설정 | `container-docker`, `kubernetes` |
