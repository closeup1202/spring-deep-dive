package com.exam.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

// 모든 통합 테스트가 이 클래스를 상속받으면 컨테이너를 공유해서 사용함
public abstract class AbstractContainerBaseTest {

    // 수동으로 start()를 호출하여 JVM 종료 시까지 유지하므로 'resource' 경고 무시
    static final GenericContainer<?> REDIS_CONTAINER;

    static {
        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse("redis:7.0.8-alpine"))
                .withExposedPorts(6379);
        
        // JVM이 종료될 때 컨테이너도 같이 종료되도록 설정 (사실 Testcontainers가 알아서 해주긴 함)
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
    }
}
