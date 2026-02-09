package com.example.actuator.endpoint;

import lombok.Data;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 캐시 통계를 조회하는 커스텀 엔드포인트
 *
 * @Selector를 사용하면 /actuator/cache-stats/{cacheName} 형태로
 * 동적 경로를 처리할 수 있습니다.
 */
@Component
@Endpoint(id = "cache-stats")
public class CacheStatsEndpoint {

    private final Random random = new Random();

    /**
     * GET /actuator/cache-stats
     * 전체 캐시 통계 조회
     */
    @ReadOperation
    public Map<String, CacheStats> getAllCacheStats() {
        Map<String, CacheStats> stats = new HashMap<>();
        stats.put("products", generateCacheStats("products"));
        stats.put("users", generateCacheStats("users"));
        stats.put("orders", generateCacheStats("orders"));
        return stats;
    }

    /**
     * GET /actuator/cache-stats/{cacheName}
     * 특정 캐시 통계 조회
     */
    @ReadOperation
    public CacheStats getCacheStats(@Selector String cacheName) {
        return generateCacheStats(cacheName);
    }

    private CacheStats generateCacheStats(String cacheName) {
        CacheStats stats = new CacheStats();
        stats.setCacheName(cacheName);
        stats.setSize(random.nextInt(1000));
        stats.setHitCount(random.nextInt(10000));
        stats.setMissCount(random.nextInt(1000));
        stats.setHitRate(calculateHitRate(stats.getHitCount(), stats.getMissCount()));
        stats.setEvictionCount(random.nextInt(100));
        return stats;
    }

    private double calculateHitRate(int hits, int misses) {
        int total = hits + misses;
        if (total == 0) return 0.0;
        return (double) hits / total * 100;
    }

    @Data
    public static class CacheStats {
        private String cacheName;
        private int size;
        private int hitCount;
        private int missCount;
        private double hitRate;
        private int evictionCount;
    }
}
