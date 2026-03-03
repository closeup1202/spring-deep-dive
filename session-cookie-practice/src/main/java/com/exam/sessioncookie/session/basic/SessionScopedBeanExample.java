package com.exam.sessioncookie.session.basic;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Session Scope Bean
 * - 세션마다 별도의 빈 인스턴스가 생성됨
 * - 세션이 유지되는 동안 상태를 보관할 수 있음
 */
@Slf4j
@RestController
@RequestMapping("/api/session/scoped")
public class SessionScopedBeanExample {

    private final UserSessionData userSessionData;

    public SessionScopedBeanExample(UserSessionData userSessionData) {
        this.userSessionData = userSessionData;
    }

    /**
     * 세션 스코프 빈에 데이터 저장
     */
    @PostMapping("/login")
    public String login(@RequestParam String username) {
        userSessionData.setUsername(username);
        userSessionData.setLoginTime(System.currentTimeMillis());
        userSessionData.setAccessCount(0);

        log.info("User logged in: {}", username);
        return "로그인 성공: " + username;
    }

    /**
     * 세션 스코프 빈에서 데이터 읽기
     */
    @GetMapping("/info")
    public UserSessionData getInfo() {
        userSessionData.incrementAccessCount();
        log.info("User session data accessed: {}", userSessionData);
        return userSessionData;
    }

    /**
     * 세션 스코프 빈: 각 세션마다 별도의 인스턴스
     */
    @Data
    @Component
    @SessionScope
    public static class UserSessionData {
        private String username;
        private long loginTime;
        private int accessCount;

        public void incrementAccessCount() {
            this.accessCount++;
        }
    }
}
