# Spring Async & Thread Pool 심화

`@Async`를 실무에서 안전하고 효율적으로 사용하기 위한 커스텀 스레드 풀 설정과 예외 처리 방법을 학습합니다.

## 1. 왜 커스텀 스레드 풀이 필요한가?
스프링의 기본 `@Async` 설정(`SimpleAsyncTaskExecutor`)은 요청이 올 때마다 **새로운 스레드를 무한정 생성**합니다. 이는 트래픽이 몰릴 경우 서버의 리소스를 고갈시켜 장애로 이어질 수 있습니다.

따라서 실무에서는 반드시 `ThreadPoolTaskExecutor`를 사용하여 스레드 개수와 대기열을 제어해야 합니다.

## 2. ExecutorService vs ThreadPoolTaskExecutor

### A. ExecutorService (Java Standard)
*   **패키지**: `java.util.concurrent`
*   **정의**: Java 5부터 도입된 비동기 작업 실행을 위한 표준 인터페이스입니다.
*   **특징**:
    *   스레드 풀을 직접 생성(`Executors.newFixedThreadPool()` 등)하고 관리해야 합니다.
    *   애플리케이션 종료 시 `shutdown()`을 명시적으로 호출하지 않으면 JVM이 종료되지 않을 수 있습니다.
    *   Spring 컨텍스트와 무관하게 순수 Java 환경에서 사용됩니다.

### B. ThreadPoolTaskExecutor (Spring Framework)
*   **패키지**: `org.springframework.scheduling.concurrent`
*   **정의**: Spring이 `java.util.concurrent.ThreadPoolExecutor`를 사용하기 쉽게 래핑(Wrapping)한 클래스입니다.
*   **특징**:
    *   **Spring Bean**으로 등록하여 관리하기 최적화되어 있습니다.
    *   **설정 편의성**: `corePoolSize`, `maxPoolSize`, `queueCapacity` 등을 JavaBean 프로퍼티 스타일(Setter)로 설정할 수 있습니다.
    *   **생명주기 관리**: Spring 컨텍스트가 종료될 때 자동으로 `shutdown`을 처리해줍니다 (`DisposableBean` 구현).
    *   `@Async` 어노테이션과 자연스럽게 연동됩니다.

**결론**: Spring 환경에서는 관리의 편의성과 `@Async`와의 호환성을 위해 **`ThreadPoolTaskExecutor`**를 사용하는 것이 표준입니다.

## 3. 핵심 설정: `ThreadPoolTaskExecutor`
*   **`corePoolSize`**: 기본적으로 유지할 스레드 수.
*   **`maxPoolSize`**: 대기열(Queue)이 꽉 찼을 때, 최대로 늘어날 수 있는 스레드 수.
*   **`queueCapacity`**: `corePoolSize`의 스레드가 모두 바쁠 때, 작업을 대기시킬 수 있는 큐의 크기.
*   **`rejectedExecutionHandler`**: `maxPoolSize`와 `queueCapacity`가 모두 찼을 때, 더 이상 작업을 받을 수 없을 때의 처리 정책.
    *   **`CallerRunsPolicy` (실무 추천)**: 작업을 요청한 스레드(예: Main 스레드)가 직접 작업을 처리하도록 함. 작업 유실을 막고, 자연스럽게 요청 속도를 제어(Back-pressure)하는 효과가 있음.

## 4. 실습 시나리오 (`AsyncRunner` 실행 결과 확인)

### 1. 예외 처리 (`AsyncUncaughtExceptionHandler`)
*   `void` 반환형을 가진 비동기 메서드에서 예외가 발생하면, `AsyncConfig`에 설정한 핸들러가 이를 잡아 로그를 남깁니다.
*   **주의**: `Future`를 반환하는 메서드의 예외는 `future.get()`을 호출할 때 잡아야 합니다.

### 2. 스레드 풀 포화 테스트 (Load Test)
*   **설정**: Core(2), Queue(10), Max(5)
*   **동작 순서**:
    1.  요청 1~2: `corePoolSize`의 스레드 2개가 처리.
    2.  요청 3~12: 스레드가 바쁘므로 `queueCapacity`(10)에 쌓임.
    3.  요청 13~15: 큐가 꽉 차서 `maxPoolSize`(5)까지 스레드 3개 추가 생성.
    4.  요청 16~20: 스레드와 큐가 모두 꽉 참. `CallerRunsPolicy`에 따라 **Main 스레드**가 직접 작업을 처리함.
*   **결과**: 로그에서 `Async-Executor-` 스레드와 `main` 스레드가 섞여서 작업을 처리하는 것을 확인할 수 있습니다.

### 3. 비동기 결과 반환 (`CompletableFuture`)
*   `@Async` 메서드가 `CompletableFuture<T>`를 반환하면, 호출자는 이 `Future` 객체를 통해 나중에 결과를 얻거나 추가적인 비동기 작업을 연결할 수 있습니다.
