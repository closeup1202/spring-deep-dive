package com.example.actuator.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 데이터베이스 연결 상태를 체크하는 헬스 인디케이터
 *
 * Spring Boot는 기본적으로 DataSourceHealthIndicator를 제공하지만,
 * 커스텀 쿼리나 추가 로직이 필요한 경우 직접 구현할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Override
    public Health health() {
        try {
            return checkDatabaseConnection();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }

    private Health checkDatabaseConnection() throws Exception {
        long startTime = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // 간단한 쿼리로 DB 연결 확인
            statement.execute("SELECT 1");

            long responseTime = System.currentTimeMillis() - startTime;

            return Health.up()
                    .withDetail("database", "H2")
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("validationQuery", "SELECT 1")
                    .build();
        }
    }
}
