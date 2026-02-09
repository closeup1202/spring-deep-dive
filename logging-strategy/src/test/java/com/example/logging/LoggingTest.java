package com.example.logging;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * MDC와 Structured Logging 동작을 확인하는 테스트
 */
@Slf4j
@SpringBootTest
class LoggingTest {

    /**
     * MDC 기본 사용법 테스트
     * MDC에 값을 넣으면 모든 로그에 자동으로 포함됩니다.
     */
    @Test
    void testMDCBasic() {
        // Given: MDC에 값 설정
        MDC.put("traceId", "test-trace-123");
        MDC.put("userId", "user-999");

        // When: 로그 출력
        log.info("This log will include traceId and userId from MDC");
        log.debug("Debug log with MDC context");
        log.warn("Warning log with MDC context");

        // Then: 콘솔에 [test-trace-123] [user-999] 가 포함된 로그가 출력됨
        MDC.clear();
    }

    /**
     * MDC가 스레드 로컬이기 때문에, 다른 스레드에서는 전파되지 않음을 확인
     */
    @Test
    void testMDCThreadLocal() throws InterruptedException {
        // Given
        MDC.put("traceId", "main-thread-trace");
        log.info("Main thread log");

        // When: 새로운 스레드 생성
        Thread newThread = new Thread(() -> {
            log.info("New thread log - MDC will be empty here!");
            // MDC 값이 전파되지 않음!
        });

        newThread.start();
        newThread.join();

        // 해결방법: TaskDecorator를 사용하여 MDC를 복사해야 함
        MDC.clear();
    }

    /**
     * 구조화된 로깅 예제
     * 로그를 나중에 파싱하기 쉽게 구조화합니다.
     */
    @Test
    void testStructuredLogging() {
        MDC.put("traceId", UUID.randomUUID().toString());
        MDC.put("userId", "test-user");

        // 구조화된 로그: key=value 형식
        log.info("action=ORDER_CREATED orderId=12345 amount=50000 currency=KRW");

        // JSON 형태로 출력 (logstash-logback-encoder 사용 시)
        log.info("Structured log with context - orderId: {}, amount: {}", "12345", 50000);

        MDC.clear();
    }

    /**
     * 여러 레벨의 메서드 호출 시 MDC 전파 확인
     */
    @Test
    void testMDCPropagation() {
        MDC.put("traceId", "propagation-test");
        MDC.put("userId", "user-001");

        log.info("Level 1: Controller layer");
        serviceLayer();

        MDC.clear();
    }

    private void serviceLayer() {
        log.info("Level 2: Service layer - MDC is automatically propagated");
        repositoryLayer();
    }

    private void repositoryLayer() {
        log.info("Level 3: Repository layer - MDC still available");
    }

    /**
     * 예외 발생 시 로깅 전략 테스트
     */
    @Test
    void testExceptionLogging() {
        MDC.put("traceId", "error-trace");
        MDC.put("userId", "error-user");

        try {
            log.info("Attempting risky operation");
            throw new IllegalArgumentException("Simulated error");
        } catch (Exception e) {
            // 예외 정보를 구조화하여 로깅
            log.error("Operation failed - errorType: {}, errorMessage: {}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e); // 마지막 파라미터로 예외 객체 전달 시 스택 트레이스 출력
        } finally {
            MDC.clear();
        }
    }
}
