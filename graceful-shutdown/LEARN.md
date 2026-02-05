# Graceful Shutdown (우아한 종료)

## 개념
애플리케이션이 종료될 때, 실행 중인 작업(Task)을 즉시 중단하지 않고 완료될 때까지 기다려주는 프로세스를 말합니다. 데이터 손실이나 상태 불일치를 방지하기 위해 필수적입니다.

## GracefulExecutorService 구현 분석

`ExecutorService`를 래핑(Decorator Pattern)하여 종료 로직을 강화한 클래스입니다.

### 종료 프로세스 (`shutdown()` 메서드)

1. **`delegate.shutdown()`**:
   - 새로운 작업(Task)의 제출(submit)을 막습니다.
   - 이미 큐에 들어간 작업이나 실행 중인 작업은 계속 진행됩니다.

2. **`delegate.awaitTermination(timeout, unit)`**:
   - 설정된 시간(`terminationTimeoutSeconds`) 동안 모든 작업이 완료되기를 기다립니다.
   - 모든 작업이 완료되면 `true`를 반환하고 정상 종료됩니다.

3. **`delegate.shutdownNow()` (타임아웃 발생 시)**:
   - 기다려도 작업이 끝나지 않으면 강제 종료를 시도합니다.
   - 실행 중인 스레드에 인터럽트(`Thread.interrupt()`)를 발생시키고, 대기 큐에 있는 작업 목록을 반환합니다.

### Spring 연동 (`@PreDestroy`)
- `@PreDestroy` 어노테이션을 사용하여 Spring 컨테이너가 빈을 파괴할 때 자동으로 `shutdown()` 메서드가 호출되도록 설정되어 있습니다.
- 이를 통해 애플리케이션 종료 시 스레드 풀도 안전하게 정리됩니다.

## 사용 예시

```java
@Bean
public GracefulExecutorService myExecutor() {
    ExecutorService delegate = Executors.newFixedThreadPool(10);
    // 30초 동안 작업 완료를 기다림
    return new GracefulExecutorService(delegate, 30);
}
```
