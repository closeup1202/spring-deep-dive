# Future & CompletableFuture

Java 비동기 프로그래밍의 핵심인 `Future`와 `CompletableFuture`를 깊이 있게 학습하는 모듈입니다.

## 목차

1. [개요](#개요)
2. [Future](#future)
3. [Future의 한계](#future의-한계)
4. [CompletableFuture 기본](#completablefuture-기본)
5. [체이닝 API](#체이닝-api)
6. [결과 조합 API](#결과-조합-api)
7. [예외 처리 API](#예외-처리-api)
8. [실전 패턴](#실전-패턴)
9. [주의사항](#주의사항)

---

## 개요

Java의 비동기 프로그래밍은 크게 두 세대로 나뉩니다.

```
1세대: Future (Java 5, 2004)
  - 비동기 작업의 결과를 담는 컨테이너
  - get()으로 결과를 가져올 때까지 블로킹
  - 체이닝, 콜백, 결과 조합 불가

2세대: CompletableFuture (Java 8, 2014)
  - Future + CompletionStage 인터페이스 구현
  - 비동기 파이프라인 구성 (체이닝)
  - 결과 조합, 예외 처리, 콜백 지원
  - 수동으로 완료 가능
```

---

## Future

### 생성

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

// Callable 제출 → Future 반환
Future<String> future = executor.submit(() -> {
    Thread.sleep(100);
    return "결과";
});
```

### 결과 가져오기

```java
// get(): 완료될 때까지 블로킹 (체크 예외 발생 가능)
String result = future.get();

// get(timeout): 타임아웃 지정
String result = future.get(1, TimeUnit.SECONDS); // TimeoutException 가능

// 완료 여부 확인 (논블로킹)
boolean done = future.isDone();
boolean cancelled = future.isCancelled();
```

### 취소

```java
// cancel(true): 실행 중인 작업 인터럽트 시도
// cancel(false): 아직 시작 안 한 작업만 취소
future.cancel(true);

future.get(); // CancellationException 발생
```

### Callable vs Runnable

| 구분 | 반환값 | 체크 예외 |
|------|--------|-----------|
| Runnable | 없음 (Future<?> = null) | 불가 |
| Callable | 있음 | 가능 |

```java
// Callable에서 발생한 예외 → ExecutionException으로 래핑
Future<String> f = executor.submit(() -> { throw new IOException("error"); });
try {
    f.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause(); // 실제 예외: IOException
}
```

### invokeAll / invokeAny

```java
List<Callable<String>> tasks = List.of(...);

// invokeAll: 모든 작업 완료 대기 → Future 목록
List<Future<String>> futures = executor.invokeAll(tasks);

// invokeAny: 가장 먼저 성공한 결과 반환, 나머지는 취소
String first = executor.invokeAny(tasks);
```

---

## Future의 한계

```
1. 블로킹 필수: get() 호출 없이는 결과를 알 수 없음
2. 체이닝 불가: 결과로 다음 작업을 자동 연결 불가
3. 조합 불가: 여러 Future 결과를 합치려면 각각 get() 호출 필요
4. 예외 처리 불편: ExecutionException을 직접 unwrap해야 함
5. 수동 완료 불가: 외부에서 Future를 완료시킬 수 없음
```

---

## CompletableFuture 기본

### 생성

```java
// 1. supplyAsync: 반환값 있는 비동기 작업
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "result");

// 2. runAsync: 반환값 없는 비동기 작업
CompletableFuture<Void> cf = CompletableFuture.runAsync(() -> System.out.println("done"));

// 3. 커스텀 Executor 지정 (I/O 집약적 작업에 권장)
ExecutorService ioPool = Executors.newFixedThreadPool(10);
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> fetchData(), ioPool);

// 4. 이미 완료된 상태
CompletableFuture<String> done = CompletableFuture.completedFuture("value");

// 5. 이미 실패한 상태 (Java 9+)
CompletableFuture<String> failed = CompletableFuture.failedFuture(new RuntimeException());
```

### 수동 완료 (CompletableFuture의 핵심 차별점)

```java
CompletableFuture<String> cf = new CompletableFuture<>();

// 어딘가에서 완료
cf.complete("value");           // 정상 완료 (이미 완료됐으면 false)
cf.completeExceptionally(ex);   // 예외로 완료
cf.obtrudeValue("forced");      // 강제 덮어쓰기 (이미 완료됐어도 적용)
```

### 결과 가져오기

```java
// get(): 체크 예외 (ExecutionException, InterruptedException)
String result = cf.get();
String result = cf.get(1, TimeUnit.SECONDS);

// join(): 언체크 예외 (CompletionException) — Stream에서 유용
String result = cf.join();

// getNow(): 완료됐으면 결과, 아니면 기본값 (블로킹 없음)
String result = cf.getNow("default");
```

### get() vs join()

```java
// get(): 체크 예외이므로 try-catch 또는 throws 선언 필요
try {
    String r = cf.get();
} catch (ExecutionException | InterruptedException e) { ... }

// join(): 언체크 예외, Stream 람다에서 유용
List<String> results = futures.stream()
    .map(CompletableFuture::join)  // throws 없이 사용 가능
    .toList();
```

---

## 체이닝 API

### thenApply — 결과 변환 (동기)

```
이전 단계와 같은 스레드에서 실행.
가벼운 변환 로직에 적합.

T → U
```

```java
CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> "hello")
    .thenApply(String::toUpperCase)    // "HELLO"
    .thenApply(s -> "[" + s + "]");    // "[HELLO]"
```

### thenApplyAsync — 결과 변환 (비동기)

```
새 스레드(ForkJoinPool 또는 지정 Executor)에서 실행.
무거운 변환 로직 또는 스레드 격리가 필요한 경우.
```

```java
cf.thenApplyAsync(s -> heavyTransform(s))                  // ForkJoinPool
cf.thenApplyAsync(s -> heavyTransform(s), myExecutor)      // 커스텀 Executor
```

### thenAccept / thenRun

```java
// thenAccept: 결과 소비 (Consumer) → CompletableFuture<Void>
cf.thenAccept(result -> saveToDb(result));

// thenRun: 결과 무관 (Runnable) → CompletableFuture<Void>
cf.thenRun(() -> sendNotification());
```

### thenCompose — 중첩 CF 평탄화 (flatMap)

```
비동기 작업이 또 다른 비동기 작업을 반환할 때 사용.
thenApply를 쓰면 CF<CF<U>>가 되어 불편함.
```

```java
// ❌ thenApply → CF<CF<String>>
CompletableFuture<CompletableFuture<String>> nested =
    fetchUserId().thenApply(id -> fetchUserName(id));

// ✅ thenCompose → CF<String> (평탄화)
CompletableFuture<String> flat =
    fetchUserId().thenCompose(id -> fetchUserName(id));
```

### 체이닝 API 요약

| 메서드 | 입력 | 출력 | 스레드 |
|--------|------|------|--------|
| `thenApply(fn)` | T → U | CF\<U\> | 동일 스레드 |
| `thenApplyAsync(fn)` | T → U | CF\<U\> | 새 스레드 |
| `thenAccept(fn)` | T → void | CF\<Void\> | 동일 스레드 |
| `thenAcceptAsync(fn)` | T → void | CF\<Void\> | 새 스레드 |
| `thenRun(fn)` | () → void | CF\<Void\> | 동일 스레드 |
| `thenCompose(fn)` | T → CF\<U\> | CF\<U\> | 비동기 |

---

## 결과 조합 API

### thenCombine — 두 CF 결과 합산

```
두 CF가 독립적으로 병렬 실행되고, 둘 다 완료되면 결과를 합산.
```

```java
CompletableFuture<UserProfile> profileFuture = fetchProfile(userId);
CompletableFuture<OrderHistory> orderFuture = fetchOrders(userId);

CompletableFuture<Dashboard> dashboard =
    profileFuture.thenCombine(orderFuture, Dashboard::new);
```

### thenAcceptBoth / runAfterBoth

```java
// 두 결과 소비
cf1.thenAcceptBoth(cf2, (r1, r2) -> log(r1, r2));

// 둘 다 완료 후 Runnable 실행
cf1.runAfterBoth(cf2, () -> cleanup());
```

### applyToEither / acceptEither — 먼저 완료된 것 사용

```java
// 캐시와 DB 중 빠른 것 사용
cacheCf.applyToEither(dbCf, result -> result.toUpperCase());
cacheCf.acceptEither(dbCf, result -> log(result));
```

### allOf — 모든 CF 완료 대기

```java
CompletableFuture<String> cf1 = fetchA();
CompletableFuture<String> cf2 = fetchB();
CompletableFuture<String> cf3 = fetchC();

// allOf는 Void 반환 → 결과는 별도 수집 필요
CompletableFuture.allOf(cf1, cf2, cf3)
    .thenApply(v -> List.of(cf1.join(), cf2.join(), cf3.join()));
```

### anyOf — 가장 먼저 완료된 CF

```java
// anyOf는 Object 반환 → 타입 캐스팅 필요
CompletableFuture.anyOf(replica1, replica2, replica3)
    .thenApply(result -> (String) result);
```

### 조합 API 요약

| 메서드 | 대상 | 동작 |
|--------|------|------|
| `thenCombine(cf, fn)` | 2개 CF | 둘 다 완료 → 결과 합산 |
| `thenAcceptBoth(cf, fn)` | 2개 CF | 둘 다 완료 → 소비 |
| `runAfterBoth(cf, fn)` | 2개 CF | 둘 다 완료 → Runnable |
| `applyToEither(cf, fn)` | 2개 CF | 먼저 완료 → 변환 |
| `acceptEither(cf, fn)` | 2개 CF | 먼저 완료 → 소비 |
| `runAfterEither(cf, fn)` | 2개 CF | 먼저 완료 → Runnable |
| `allOf(cfs...)` | N개 CF | 전부 완료 대기 → Void |
| `anyOf(cfs...)` | N개 CF | 첫 완료 결과 → Object |

---

## 예외 처리 API

### 예외 전파 규칙

```
파이프라인 중간에서 예외 발생 시:
- thenApply, thenAccept, thenRun, thenCompose → 건너뜀 (예외 전파)
- exceptionally → 예외가 있을 때만 호출
- handle → 성공/실패 모두 호출
- whenComplete → 성공/실패 모두 호출 (결과 변환 불가)
```

```java
CompletableFuture.supplyAsync(() -> "data")
    .thenApply(s -> { throw new RuntimeException("error"); }) // 예외 발생
    .thenApply(s -> s + "-step2")   // 건너뜀
    .thenApply(s -> s + "-step3")   // 건너뜀
    .exceptionally(ex -> "recovered"); // 여기서 캐치
```

### exceptionally — 예외 발생 시 복구

```java
// 정상 완료 시 호출 안됨
// 예외 발생 시 대체값 반환 → 파이프라인 계속 진행
cf.exceptionally(ex -> {
    log.warn("Error: {}", ex.getMessage());
    return "fallback";
});
```

### handle — 성공/실패 모두 처리

```java
// 항상 호출됨, 결과를 변환할 수 있음
cf.handle((result, ex) -> {
    if (ex != null) return "fallback: " + ex.getMessage();
    return result.toUpperCase(); // 성공 시 변환
});
```

### whenComplete — 관찰만 (변환 불가)

```java
// 항상 호출됨, 결과/예외를 그대로 전달 (변환 없음)
cf.whenComplete((result, ex) -> {
    if (ex != null) log.error("Failed: {}", ex.getMessage());
    else log.info("Success: {}", result);
    // 반환값 없음 → 원래 result/ex 그대로 전파됨
});
```

### 세 가지 예외 처리 비교

| 메서드 | 호출 조건 | 결과 변환 | 사용 목적 |
|--------|-----------|-----------|-----------|
| `exceptionally(fn)` | 예외 시만 | 가능 | 예외 복구, 기본값 반환 |
| `handle(fn)` | 항상 | 가능 | 성공/실패 통합 처리 |
| `whenComplete(fn)` | 항상 | 불가 | 로깅, 모니터링, 사이드이펙트 |

---

## 실전 패턴

### 패턴 1: 병렬 마이크로서비스 집계

```java
// 세 서비스를 병렬로 호출하고 결과 합산
CompletableFuture<User> userCf   = userService.findUser(id);
CompletableFuture<Order> orderCf = orderService.getOrders(id);
CompletableFuture<Point> pointCf = pointService.getPoints(id);

CompletableFuture.allOf(userCf, orderCf, pointCf)
    .thenApply(v -> new Dashboard(
        userCf.join(), orderCf.join(), pointCf.join()
    ));
```

### 패턴 2: 비동기 파이프라인

```java
// 검증 → DB 조회 → 가공 → 저장
CompletableFuture.supplyAsync(() -> validate(input))
    .thenCompose(valid -> repository.findAsync(valid.id()))
    .thenApply(entity -> transform(entity))
    .thenCompose(dto -> repository.saveAsync(dto))
    .whenComplete((saved, ex) -> {
        if (ex != null) metrics.recordFailure();
        else metrics.recordSuccess();
    });
```

### 패턴 3: 타임아웃 처리 (Java 9+)

```java
// orTimeout: 타임아웃 시 TimeoutException으로 예외 완료
cf.orTimeout(500, TimeUnit.MILLISECONDS);

// completeOnTimeout: 타임아웃 시 기본값으로 정상 완료
cf.completeOnTimeout("default", 500, TimeUnit.MILLISECONDS);
```

### 패턴 4: 캐시 우선 조회

```java
CompletableFuture<String> cacheCf = cacheService.get(key);
CompletableFuture<String> dbCf = dbService.get(key);

// 캐시 먼저, 없으면 DB
cacheCf.applyToEither(dbCf, result -> result);
```

### 패턴 5: 재시도 (thenCompose 활용)

```java
CompletableFuture<String> withRetry(Supplier<CompletableFuture<String>> task, int retries) {
    return task.get().exceptionallyCompose(ex -> {
        if (retries > 0) return withRetry(task, retries - 1);
        return CompletableFuture.failedFuture(ex);
    });
}
```

---

## 주의사항

### 1. ForkJoinPool 고갈 방지

```java
// ❌ I/O 작업에 commonPool 사용 → 스레드 고갈 위험
CompletableFuture.supplyAsync(() -> callExternalApi());

// ✅ I/O 작업에는 별도 Executor 사용
ExecutorService ioPool = Executors.newFixedThreadPool(50);
CompletableFuture.supplyAsync(() -> callExternalApi(), ioPool);
```

**이유**: ForkJoinPool.commonPool은 CPU 코어 수에 맞게 크기 제한됨. I/O 블로킹 작업이 많으면 다른 작업이 대기함.

### 2. thenApply vs thenApplyAsync 선택

```java
// thenApply: 이전 스레드에서 실행 (가벼운 변환에 적합)
cf.thenApply(s -> s.toUpperCase());

// thenApplyAsync: 새 스레드에서 실행 (무거운 작업 또는 스레드 격리 필요 시)
cf.thenApplyAsync(s -> heavyComputation(s), myExecutor);
```

### 3. join()은 스트림에서 유용

```java
// ❌ get()은 체크 예외로 람다에서 불편
List<String> results = futures.stream()
    .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
    .toList();

// ✅ join()은 언체크 예외
List<String> results = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

### 4. allOf 결과 수집 패턴

```java
// allOf는 CompletableFuture<Void> 반환 → 결과를 별도로 join()으로 수집
List<CompletableFuture<String>> futures = ...;

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenApply(v -> futures.stream()
        .map(CompletableFuture::join)  // 이미 완료됐으므로 블로킹 없음
        .toList());
```

### 5. 예외 처리는 파이프라인 끝에

```java
// ✅ 파이프라인 끝에 한 번 처리
cf.thenApply(step1)
  .thenApply(step2)
  .thenCompose(step3)
  .exceptionally(ex -> "fallback"); // 어느 단계에서든 예외가 나면 여기서 처리
```

### 6. 실행 중인 CF는 cancel() 이 어려움

```java
// Future와 달리 CompletableFuture는 cancel(true)가
// 실제 작업 스레드를 인터럽트하지 않음.
// cancel()은 CF 자체를 CancellationException으로 완료시킬 뿐.
cf.cancel(true); // 내부 작업은 계속 실행될 수 있음
```

---

## Future vs CompletableFuture 비교

| 항목 | Future | CompletableFuture |
|------|--------|------------------|
| 도입 버전 | Java 5 | Java 8 |
| 결과 대기 | 블로킹 get() 필수 | 콜백 체이닝 가능 |
| 체이닝 | 불가 | thenApply/thenCompose 등 |
| 결과 조합 | 불가 | thenCombine, allOf, anyOf |
| 예외 처리 | ExecutionException 직접 처리 | exceptionally, handle, whenComplete |
| 수동 완료 | 불가 | complete(), completeExceptionally() |
| 취소 | cancel() 지원 | cancel() 지원 (작업 인터럽트 미보장) |

---

## 학습 체크리스트

- [ ] Future의 생성, get(), cancel(), isDone() 사용법 이해
- [ ] Callable vs Runnable 차이 이해
- [ ] Future의 블로킹 한계 이해
- [ ] CompletableFuture의 supplyAsync/runAsync 사용법 이해
- [ ] 수동 완료(complete/completeExceptionally) 이해
- [ ] get() vs join() 차이 이해
- [ ] thenApply vs thenApplyAsync 차이 이해
- [ ] thenCompose(flatMap) 필요성 이해
- [ ] thenCombine, allOf, anyOf 사용법 이해
- [ ] exceptionally / handle / whenComplete 차이 이해
- [ ] ForkJoinPool 고갈 방지를 위한 Executor 분리 이해
- [ ] 예외 전파 규칙 이해

---

## 테스트 실행

```bash
# 전체 테스트
./gradlew :future-completablefuture:test

# Future 기본 테스트
./gradlew :future-completablefuture:test --tests FutureExampleTest

# CompletableFuture 기본 테스트
./gradlew :future-completablefuture:test --tests CompletableFutureBasicExampleTest

# 체이닝 테스트
./gradlew :future-completablefuture:test --tests CompletableFutureChainExampleTest

# 조합 테스트
./gradlew :future-completablefuture:test --tests CompletableFutureCombineExampleTest

# 예외 처리 테스트
./gradlew :future-completablefuture:test --tests CompletableFutureExceptionExampleTest
```

---

## 참고 자료

- **Java Concurrency in Practice** (Brian Goetz)
- [CompletableFuture JavaDoc](https://docs.oracle.com/en/java/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [CompletionStage JavaDoc](https://docs.oracle.com/en/java/docs/api/java.base/java/util/concurrent/CompletionStage.html)
- [Baeldung: Guide to CompletableFuture](https://www.baeldung.com/java-completablefuture)
