package com.exam.sessioncookie.session.advanced;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Spring Session + Redis 활용
 * - 분산 환경에서 세션 공유
 * - 세션 클러스터링
 * - 세션 검색 및 관리
 */
@Slf4j
@RestController
@RequestMapping("/api/session/redis")
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class RedisSessionController {

    private final RedisIndexedSessionRepository sessionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 1. Redis 기반 세션 생성
     * - Spring Session이 자동으로 Redis에 저장
     */
    @PostMapping("/create")
    public Map<String, Object> createRedisSession(HttpServletRequest request) {
        HttpSession session = request.getSession(true);

        session.setAttribute("username", "redis-user");
        session.setAttribute("role", "ADMIN");
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "redis-user");

        log.info("Redis session created: ID={}", session.getId());

        return Map.of(
                "sessionId", session.getId(),
                "message", "Redis 세션이 생성되었습니다.",
                "attributes", getAllSessionAttributes(session)
        );
    }

    /**
     * 2. 사용자명으로 세션 검색
     * - FindByIndexNameSessionRepository를 사용하여 특정 사용자의 모든 세션 조회
     */
    @GetMapping("/find-by-username/{username}")
    public Map<String, Object> findSessionsByUsername(@PathVariable String username) {
        Map<String, RedisIndexedSessionRepository.RedisSession> sessions = sessionRepository.findByPrincipalName(username);

        List<Map<String, Object>> sessionList = new ArrayList<>();
        sessions.forEach((sessionId, session) -> {
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("sessionId", sessionId);
            sessionInfo.put("creationTime", session.getCreationTime());
            sessionInfo.put("lastAccessedTime", session.getLastAccessedTime());
            sessionInfo.put("maxInactiveInterval", session.getMaxInactiveInterval().getSeconds());
            sessionInfo.put("attributes", session.getAttributeNames());

            sessionList.add(sessionInfo);
        });

        log.info("Found {} sessions for user: {}", sessionList.size(), username);

        return Map.of(
                "username", username,
                "sessionCount", sessionList.size(),
                "sessions", sessionList
        );
    }

    /**
     * 3. 모든 활성 세션 조회
     * - Redis에 저장된 모든 세션 키 스캔
     */
    @GetMapping("/all")
    public Map<String, Object> getAllActiveSessions() {
        Set<String> keys = redisTemplate.keys("spring:session:sessions:*");

        if (keys == null) {
            return Map.of("message", "활성 세션이 없습니다.", "count", 0);
        }

        List<String> sessionIds = keys.stream()
                .map(key -> key.replace("spring:session:sessions:", ""))
                .toList();

        log.info("Total active sessions: {}", sessionIds.size());

        return Map.of(
                "count", sessionIds.size(),
                "sessionIds", sessionIds
        );
    }

    /**
     * 4. 특정 사용자의 모든 세션 종료 (강제 로그아웃)
     * - 보안상의 이유로 특정 사용자의 모든 세션을 종료해야 할 때 사용
     */
    @DeleteMapping("/revoke/{username}")
    public Map<String, Object> revokeAllUserSessions(@PathVariable String username) {
        Map<String, RedisIndexedSessionRepository.RedisSession> sessions = sessionRepository.findByPrincipalName(username);

        int revokedCount = 0;
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
            revokedCount++;
            log.info("Session revoked: sessionId={}, username={}", sessionId, username);
        }

        return Map.of(
                "username", username,
                "revokedSessionCount", revokedCount,
                "message", revokedCount + "개의 세션이 종료되었습니다."
        );
    }

    /**
     * 5. 세션 만료 시간 동적 변경
     */
    @PutMapping("/extend/{sessionId}")
    public Map<String, Object> extendSession(@PathVariable String sessionId, @RequestParam int seconds) {
        RedisIndexedSessionRepository.RedisSession session = sessionRepository.findById(sessionId);

        if (session == null) {
            return Map.of("message", "세션을 찾을 수 없습니다.");
        }

        session.setMaxInactiveInterval(java.time.Duration.ofSeconds(seconds));
        sessionRepository.save(session);

        log.info("Session extended: sessionId={}, newTimeout={}s", sessionId, seconds);

        return Map.of(
                "sessionId", sessionId,
                "newTimeout", seconds,
                "message", "세션 만료 시간이 연장되었습니다."
        );
    }

    /**
     * 6. 세션 데이터 통계
     */
    @GetMapping("/stats")
    public Map<String, Object> getSessionStats() {
        Set<String> keys = redisTemplate.keys("spring:session:sessions:*");
        int totalSessions = keys != null ? keys.size() : 0;

        // 활성 사용자 수 계산 (중복 제거)
        Set<String> activeUsers = new HashSet<>();
        if (keys != null) {
            for (String key : keys) {
                String sessionId = key.replace("spring:session:sessions:", "");
                RedisIndexedSessionRepository.RedisSession session = sessionRepository.findById(sessionId);
                if (session != null) {
                    String username = session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
                    if (username != null) {
                        activeUsers.add(username);
                    }
                }
            }
        }

        return Map.of(
                "totalSessions", totalSessions,
                "activeUsers", activeUsers.size(),
                "userList", activeUsers
        );
    }

    // Helper method
    private Map<String, Object> getAllSessionAttributes(HttpSession session) {
        Map<String, Object> attributes = new HashMap<>();
        session.getAttributeNames().asIterator().forEachRemaining(name -> {
            attributes.put(name, session.getAttribute(name));
        });
        return attributes;
    }
}
