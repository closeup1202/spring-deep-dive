package com.exam.sessioncookie.session.basic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * HttpSession 기본 사용법
 * - 세션 생성, 조회, 삭제
 * - 세션 ID 확인
 * - 세션 타임아웃 설정
 */
@Slf4j
@RestController
@RequestMapping("/api/session/basic")
public class HttpSessionBasicController {

    /**
     * 1. 세션 생성 - 기본 방식
     */
    @PostMapping("/create")
    public Map<String, Object> createSession(HttpServletRequest request) {
        // true: 세션이 없으면 새로 생성, false: 세션이 없으면 null 반환
        HttpSession session = request.getSession(true);

        session.setAttribute("username", "user123");
        session.setAttribute("role", "USER");
        session.setAttribute("loginTime", System.currentTimeMillis());

        log.info("Session created: ID={}, isNew={}", session.getId(), session.isNew());

        return Map.of(
                "sessionId", session.getId(),
                "isNew", session.isNew(),
                "maxInactiveInterval", session.getMaxInactiveInterval()
        );
    }

    /**
     * 2. 세션 조회
     */
    @GetMapping("/info")
    public Map<String, Object> getSessionInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 존재하지 않습니다.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("username", session.getAttribute("username"));
        result.put("role", session.getAttribute("role"));
        result.put("loginTime", session.getAttribute("loginTime"));
        result.put("creationTime", session.getCreationTime());
        result.put("lastAccessedTime", session.getLastAccessedTime());
        result.put("maxInactiveInterval", session.getMaxInactiveInterval());

        log.info("Session info: {}", result);

        return result;
    }

    /**
     * 3. 세션 타임아웃 설정 (초 단위)
     */
    @PutMapping("/timeout/{seconds}")
    public Map<String, Object> setTimeout(HttpServletRequest request, @PathVariable int seconds) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 존재하지 않습니다.");
        }

        int oldTimeout = session.getMaxInactiveInterval();
        session.setMaxInactiveInterval(seconds);

        log.info("Session timeout changed: {} -> {}", oldTimeout, seconds);

        return Map.of(
                "oldTimeout", oldTimeout,
                "newTimeout", session.getMaxInactiveInterval()
        );
    }

    /**
     * 4. 특정 속성 업데이트
     */
    @PutMapping("/update")
    public Map<String, Object> updateAttribute(
            HttpServletRequest request,
            @RequestParam String key,
            @RequestParam String value) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 존재하지 않습니다.");
        }

        Object oldValue = session.getAttribute(key);
        session.setAttribute(key, value);

        log.info("Session attribute updated: key={}, oldValue={}, newValue={}", key, oldValue, value);

        return Map.of(
                "key", key,
                "oldValue", oldValue != null ? oldValue : "null",
                "newValue", value
        );
    }

    /**
     * 5. 특정 속성 삭제
     */
    @DeleteMapping("/remove/{key}")
    public Map<String, Object> removeAttribute(HttpServletRequest request, @PathVariable String key) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 존재하지 않습니다.");
        }

        Object removedValue = session.getAttribute(key);
        session.removeAttribute(key);

        log.info("Session attribute removed: key={}, value={}", key, removedValue);

        return Map.of(
                "key", key,
                "removedValue", removedValue != null ? removedValue : "null"
        );
    }

    /**
     * 6. 세션 무효화 (로그아웃)
     */
    @PostMapping("/invalidate")
    public Map<String, String> invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 이미 존재하지 않습니다.");
        }

        String sessionId = session.getId();
        session.invalidate();

        log.info("Session invalidated: {}", sessionId);

        return Map.of("message", "세션이 무효화되었습니다.", "sessionId", sessionId);
    }

    /**
     * 7. 세션의 모든 속성 조회
     */
    @GetMapping("/attributes")
    public Map<String, Object> getAllAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 존재하지 않습니다.");
        }

        Map<String, Object> attributes = new HashMap<>();
        session.getAttributeNames().asIterator().forEachRemaining(name -> {
            attributes.put(name, session.getAttribute(name));
        });

        return Map.of(
                "sessionId", session.getId(),
                "attributes", attributes
        );
    }
}
