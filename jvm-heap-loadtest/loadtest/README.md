# 부하 테스트 스크립트

JDK 만으로 돌아가는 내장 생성기(`LoadGenerator`)와, 실무에서 쓰는 k6 스크립트를 둘 다 둔다.

## 1. 설치 없이 (내장 생성기)

```bash
./gradlew :jvm-heap-loadtest:loadTest
```

`LoadScenarioTest` 의 4개 시나리오(baseline / saturation / leak / ramp-up)가 돌고
지연 백분위수와 GC·힙 변화가 같은 리포트에 찍힌다.

한계: 부하 생성기와 서버가 **같은 JVM** 에서 돈다. CPU 와 GC 를 공유하므로
절대 수치는 신뢰하면 안 된다. 시나리오 간 상대 비교용이다.

## 2. k6 (권장)

```bash
# 설치 (Windows)
winget install k6 --source winget
# 또는 choco install k6

# 앱 실행
./gradlew :jvm-heap-loadtest:bootRun

# 시나리오 실행
k6 run loadtest/k6/smoke.js
k6 run loadtest/k6/load.js
k6 run loadtest/k6/stress.js
k6 run loadtest/k6/spike.js
k6 run loadtest/k6/soak.js

# 대상 주소 바꾸기
k6 run -e BASE_URL=http://localhost:9090 loadtest/k6/load.js
```

## 3. 시나리오 종류

| 종류 | 목적 | 부하 | 무엇을 잡는가 |
|---|---|---|---|
| smoke | 스크립트/환경 점검 | VU 1~2, 30초 | 배포 직후의 기본 동작 |
| load | 평상시 트래픽 재현 | 목표 TPS, 5~30분 | SLO 위반 여부 |
| stress | 한계점 탐색 | 계속 증가 | saturation point, 붕괴 양상 |
| spike | 순간 급증 | 급증 후 급감 | 버티는가 + **회복하는가** |
| soak | 내구성 | 낮은 부하, 수 시간 | **메모리/커넥션 누수** |

이 모듈의 두 주제가 만나는 지점은 **soak** 다. 누수는 짧은 부하 테스트로 절대 안 잡힌다.

## 4. 도구 선택

| 도구 | 언어 | 특징 |
|---|---|---|
| k6 | JS | 가볍고 CLI 친화적. threshold 로 CI 게이트를 걸기 좋다. 현재 기본 선택지 |
| Gatling | Scala/Java/Kotlin | JVM 기반, 리포트가 훌륭하다. 시나리오가 복잡할 때 |
| JMeter | GUI/XML | 오래됐지만 프로토콜 지원이 넓다. GUI 실행은 절대 금지(부하 생성기 자체가 병목) |
| nGrinder | Java/Groovy | 국내 사용 사례가 많다. 분산 부하 관리 콘솔 제공 |
| hey / wrk | - | 단일 엔드포인트를 빠르게 때려볼 때 |

## 5. 부하 테스트에서 반드시 지킬 것

1. **부하 생성기를 대상 서버와 같은 머신에 두지 않는다.** 서로 CPU 를 뺏는다.
2. **워밍업 구간을 통계에서 뺀다.** JVM 은 예열 전 10배 느리다.
3. **평균을 보지 않는다.** p95/p99 와 max 를 본다.
4. **서버 쪽 지표를 같이 본다.** 클라이언트 지연만 보면 원인을 못 찾는다.
   (GC pause, 힙 사용량, 스레드 수, 커넥션 풀 대기)
5. **한 번에 하나만 바꾼다.** 힙 크기와 GC 를 동시에 바꾸면 무엇이 효과였는지 모른다.
6. **에러율이 올라간 뒤의 지연 숫자는 의미가 없다.** 실패한 요청은 빨리 끝나서 p95 를 오히려 낮춘다.
