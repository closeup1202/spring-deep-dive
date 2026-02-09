package com.example.actuator.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 커스텀 헬스 체크 예제
 *
 * Spring Actuator는 /actuator/health 엔드포인트에서
 * 모든 HealthIndicator를 자동으로 수집하여 전체 시스템 상태를 보고합니다.
 *
 * 상태 종류:
 * - UP: 정상
 * - DOWN: 장애 (HTTP 503 반환)
 * - OUT_OF_SERVICE: 서비스 중단 (점검 등)
 * - UNKNOWN: 알 수 없음
 */
@Slf4j
@Component
public class CustomHealthIndicator implements HealthIndicator {

    private final Random random = new Random();

    @Override
    public Health health() {
        log.debug("Checking custom health indicator");

        // 실제로는 외부 시스템(DB, Redis, Kafka 등) 연결 상태를 체크
        boolean isHealthy = checkExternalSystem();

        if (isHealthy) {
            return Health.up()
                    .withDetail("service", "CustomService")
                    .withDetail("status", "All systems operational")
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        } else {
            return Health.down()
                    .withDetail("service", "CustomService")
                    .withDetail("error", "External system unavailable")
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * 외부 시스템 상태 체크 시뮬레이션
     * 실제로는 DB 쿼리, Redis ping, HTTP 요청 등을 수행
     */
    private boolean checkExternalSystem() {
        // 80% 확률로 정상
        return random.nextInt(100) < 80;
    }
}
