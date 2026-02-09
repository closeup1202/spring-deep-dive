package com.example.actuator.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 외부 API 연동 상태를 체크하는 헬스 인디케이터
 *
 * 실무에서는 결제 게이트웨이, SMS 발송 API, 외부 파트너 API 등의
 * 연결 상태를 주기적으로 체크합니다.
 */
@Slf4j
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Health health() {
        try {
            return checkExternalApi();
        } catch (Exception e) {
            log.error("External API health check failed", e);
            return Health.down()
                    .withDetail("api", "ExternalService")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private Health checkExternalApi() {
        long startTime = System.currentTimeMillis();

        try {
            // 실제로는 외부 API의 헬스체크 엔드포인트를 호출
            // 예: restTemplate.getForObject("https://api.example.com/health", String.class);

            // 시뮬레이션: 랜덤하게 성공/실패
            if (Math.random() > 0.1) { // 90% 성공
                long responseTime = System.currentTimeMillis() - startTime;

                return Health.up()
                        .withDetail("api", "ExternalService")
                        .withDetail("endpoint", "https://api.example.com/health")
                        .withDetail("responseTime", responseTime + "ms")
                        .build();
            } else {
                return Health.down()
                        .withDetail("api", "ExternalService")
                        .withDetail("error", "Connection timeout")
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("External API is unreachable", e);
        }
    }
}
