package com.exam.redis;

import com.exam.redis.service.HotDealService;
import com.exam.redis.service.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisDeepDiveTest {

    @Autowired
    private HotDealService hotDealService;

    @Autowired
    private RankingService rankingService;

    @Test
    @DisplayName("분산 락: 동시에 5명이 구매를 시도해도 순차적으로 처리되어야 한다")
    void distributedLockTest() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            String userId = "user-" + i;
            executorService.submit(() -> {
                try {
                    hotDealService.purchaseItem(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        // 로그를 확인해보면 "구매 로직 진입" -> "구매 완료"가 순차적으로 찍혀야 함 (락 때문)
        // 락이 없으면 "진입" 로그가 우르르 찍힘
    }

    @Test
    @DisplayName("랭킹: 점수가 높은 순서대로 조회되어야 한다")
    void rankingTest() {
        // Given
        rankingService.addScore("userA", 100);
        rankingService.addScore("userB", 300); // 1등
        rankingService.addScore("userC", 200);

        // When
        Set<String> topRankers = rankingService.getTopRank(3);

        // Then
        assertThat(topRankers).containsExactly("userB", "userC", "userA");
        
        Long rankB = rankingService.getUserRank("userB");
        assertThat(rankB).isEqualTo(0); // 0등 (1위)
    }
}
