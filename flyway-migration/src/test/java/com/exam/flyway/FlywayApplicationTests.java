package com.exam.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 컨텍스트 로딩 = Flyway 마이그레이션 실행 + Hibernate 스키마 검증.
 * 즉 이 테스트 하나가 "엔티티와 마이그레이션이 어긋나지 않았다"를 보장한다.
 *
 * <p>local 프로필이라 db/seed 까지 함께 적용된다.
 * STEPS 11단계에서 실제로 깨지는 쪽이 이 테스트다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class FlywayApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
