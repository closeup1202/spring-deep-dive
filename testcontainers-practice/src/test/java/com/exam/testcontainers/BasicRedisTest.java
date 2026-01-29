package com.exam.testcontainers;

import com.exam.testcontainers.service.RedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers // Testcontainers 활성화
class BasicRedisTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.0.8-alpine"));

    @Autowired
    private RedisService redisService;

    // 컨테이너가 랜덤 포트로 뜨기 때문에, Spring Boot 설정에 동적으로 포트를 주입해줘야 함
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Test
    @DisplayName("Redis 컨테이너가 정상적으로 동작해야 한다")
    void testRedis() {
        // given
        String key = "testKey";
        String value = "Hello Testcontainers";

        // when
        redisService.setValue(key, value);
        String result = redisService.getValue(key);

        // then
        assertThat(result).isEqualTo(value);
    }
}
