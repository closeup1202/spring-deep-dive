package com.exam.testcontainers;

import com.exam.testcontainers.service.RedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SingletonRedisTest extends AbstractContainerBaseTest {

    @Autowired
    private RedisService redisService;

    @Test
    @DisplayName("싱글톤 컨테이너를 사용하여 Redis 테스트 1")
    void test1() {
        redisService.setValue("key1", "value1");
        assertThat(redisService.getValue("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("싱글톤 컨테이너를 사용하여 Redis 테스트 2")
    void test2() {
        redisService.setValue("key2", "value2");
        assertThat(redisService.getValue("key2")).isEqualTo("value2");
    }
}
