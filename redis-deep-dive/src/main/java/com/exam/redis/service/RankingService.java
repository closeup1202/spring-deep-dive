package com.exam.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String LEADERBOARD_KEY = "game:leaderboard";

    // 점수 추가 (ZADD)
    public void addScore(String userId, double score) {
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, userId, score);
    }

    // 상위 랭커 조회 (ZREVRANGE)
    public Set<String> getTopRank(int limit) {
        // 0부터 limit-1까지 (점수 높은 순)
        return redisTemplate.opsForZSet().reverseRange(LEADERBOARD_KEY, 0, limit - 1);
    }
    
    // 특정 유저 랭킹 조회 (ZREVRANK)
    public Long getUserRank(String userId) {
        return redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, userId);
    }
}
