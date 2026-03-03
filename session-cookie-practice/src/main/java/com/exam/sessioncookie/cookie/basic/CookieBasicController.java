package com.exam.sessioncookie.cookie.basic;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Cookie 기본 사용법
 * - 쿠키 생성, 조회, 수정, 삭제
 * - 쿠키 속성 설정 (MaxAge, Path, Domain, HttpOnly, Secure, SameSite)
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/basic")
public class CookieBasicController {

    /**
     * 1. 쿠키 생성 - 기본
     */
    @PostMapping("/create")
    public Map<String, String> createCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("username", "john_doe");
        cookie.setMaxAge(3600); // 1시간 (초 단위)
        cookie.setPath("/");     // 모든 경로에서 접근 가능

        response.addCookie(cookie);

        log.info("Cookie created: name={}, value={}, maxAge={}", cookie.getName(), cookie.getValue(), cookie.getMaxAge());

        return Map.of(
                "message", "쿠키가 생성되었습니다.",
                "cookieName", cookie.getName(),
                "cookieValue", cookie.getValue()
        );
    }

    /**
     * 2. 여러 쿠키 동시 생성
     */
    @PostMapping("/create-multiple")
    public Map<String, String> createMultipleCookies(HttpServletResponse response) {
        Cookie cookie1 = new Cookie("userId", "12345");
        cookie1.setMaxAge(7200);
        cookie1.setPath("/");

        Cookie cookie2 = new Cookie("theme", "dark");
        cookie2.setMaxAge(86400); // 24시간
        cookie2.setPath("/");

        Cookie cookie3 = new Cookie("language", "ko");
        cookie3.setMaxAge(2592000); // 30일
        cookie3.setPath("/");

        response.addCookie(cookie1);
        response.addCookie(cookie2);
        response.addCookie(cookie3);

        log.info("Multiple cookies created");

        return Map.of("message", "3개의 쿠키가 생성되었습니다.");
    }

    /**
     * 3. 쿠키 조회 - 모든 쿠키
     */
    @GetMapping("/all")
    public Map<String, Object> getAllCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null || cookies.length == 0) {
            return Map.of("message", "쿠키가 없습니다.");
        }

        Map<String, String> cookieMap = new HashMap<>();
        for (Cookie cookie : cookies) {
            cookieMap.put(cookie.getName(), cookie.getValue());
            log.info("Cookie found: {}={}", cookie.getName(), cookie.getValue());
        }

        return Map.of(
                "count", cookies.length,
                "cookies", cookieMap
        );
    }

    /**
     * 4. 특정 쿠키 조회
     */
    @GetMapping("/find/{cookieName}")
    public Map<String, Object> findCookie(HttpServletRequest request, @PathVariable String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Map.of("message", "쿠키가 없습니다.");
        }

        Cookie foundCookie = Arrays.stream(cookies)
                .filter(cookie -> cookie.getName().equals(cookieName))
                .findFirst()
                .orElse(null);

        if (foundCookie == null) {
            return Map.of("message", "해당 쿠키를 찾을 수 없습니다: " + cookieName);
        }

        log.info("Cookie found: {}={}", foundCookie.getName(), foundCookie.getValue());

        return Map.of(
                "name", foundCookie.getName(),
                "value", foundCookie.getValue(),
                "path", foundCookie.getPath(),
                "maxAge", foundCookie.getMaxAge()
        );
    }

    /**
     * 5. 쿠키 수정 (덮어쓰기)
     * 참고: 쿠키는 수정이 불가능하므로, 같은 이름으로 새 쿠키를 생성하여 덮어씀
     */
    @PutMapping("/update/{cookieName}")
    public Map<String, String> updateCookie(
            HttpServletResponse response,
            @PathVariable String cookieName,
            @RequestParam String newValue) {

        Cookie cookie = new Cookie(cookieName, newValue);
        cookie.setMaxAge(3600);
        cookie.setPath("/");

        response.addCookie(cookie);

        log.info("Cookie updated: {}={}", cookieName, newValue);

        return Map.of(
                "message", "쿠키가 수정되었습니다.",
                "cookieName", cookieName,
                "newValue", newValue
        );
    }

    /**
     * 6. 쿠키 삭제
     * 참고: MaxAge를 0으로 설정하면 브라우저에서 즉시 삭제됨
     */
    @DeleteMapping("/delete/{cookieName}")
    public Map<String, String> deleteCookie(HttpServletResponse response, @PathVariable String cookieName) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setMaxAge(0);    // 즉시 만료
        cookie.setPath("/");     // 원본 쿠키와 동일한 Path 설정 필요

        response.addCookie(cookie);

        log.info("Cookie deleted: {}", cookieName);

        return Map.of(
                "message", "쿠키가 삭제되었습니다.",
                "cookieName", cookieName
        );
    }

    /**
     * 7. 쿠키 속성 설정 - Path
     * Path를 지정하면 해당 경로에서만 쿠키가 전송됨
     */
    @PostMapping("/create-with-path")
    public Map<String, String> createCookieWithPath(HttpServletResponse response) {
        Cookie cookie = new Cookie("admin-token", "secret123");
        cookie.setMaxAge(1800);
        cookie.setPath("/api/admin");  // /api/admin/** 경로에서만 전송

        response.addCookie(cookie);

        log.info("Cookie with path created: path={}", cookie.getPath());

        return Map.of(
                "message", "Path가 지정된 쿠키가 생성되었습니다.",
                "path", "/api/admin"
        );
    }

    /**
     * 8. 쿠키 속성 - HttpOnly, Secure
     * HttpOnly: JavaScript에서 접근 불가 (XSS 방어)
     * Secure: HTTPS에서만 전송
     */
    @PostMapping("/create-secure")
    public Map<String, String> createSecureCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("session-token", "abc123xyz");
        cookie.setMaxAge(3600);
        cookie.setPath("/");
        cookie.setHttpOnly(true);  // JavaScript document.cookie로 접근 불가
        cookie.setSecure(true);     // HTTPS에서만 전송 (개발 환경에서는 무시됨)

        response.addCookie(cookie);

        log.info("Secure cookie created: HttpOnly={}, Secure={}", cookie.isHttpOnly(), cookie.getSecure());

        return Map.of(
                "message", "보안 쿠키가 생성되었습니다.",
                "httpOnly", String.valueOf(cookie.isHttpOnly()),
                "secure", String.valueOf(cookie.getSecure())
        );
    }

    /**
     * 9. 쿠키 속성 - SameSite
     * SameSite: CSRF 공격 방어
     * - Strict: 동일 사이트에서만 전송
     * - Lax: 안전한 메서드(GET)로 이동할 때만 전송
     * - None: 항상 전송 (Secure 필수)
     */
    @PostMapping("/create-samesite")
    public Map<String, String> createCookieWithSameSite(HttpServletResponse response) {
        // Spring Boot 3.x에서는 Set-Cookie 헤더를 직접 설정
        String cookieHeader = "sameSiteToken=xyz789; Max-Age=3600; Path=/; HttpOnly; Secure; SameSite=Strict";
        response.addHeader("Set-Cookie", cookieHeader);

        log.info("Cookie with SameSite created");

        return Map.of(
                "message", "SameSite 속성이 설정된 쿠키가 생성되었습니다.",
                "sameSite", "Strict"
        );
    }

    /**
     * 10. 세션 쿠키 vs 영구 쿠키
     * - MaxAge 미설정 또는 -1: 세션 쿠키 (브라우저 종료 시 삭제)
     * - MaxAge > 0: 영구 쿠키 (지정된 시간 후 삭제)
     */
    @PostMapping("/create-session-cookie")
    public Map<String, String> createSessionCookie(HttpServletResponse response) {
        Cookie sessionCookie = new Cookie("temp-data", "temporary");
        sessionCookie.setPath("/");
        // MaxAge를 설정하지 않으면 세션 쿠키

        Cookie persistentCookie = new Cookie("remember-me", "user123");
        persistentCookie.setMaxAge(2592000); // 30일
        persistentCookie.setPath("/");

        response.addCookie(sessionCookie);
        response.addCookie(persistentCookie);

        log.info("Session cookie and persistent cookie created");

        return Map.of(
                "message", "세션 쿠키와 영구 쿠키가 생성되었습니다.",
                "sessionCookie", "temp-data (브라우저 종료 시 삭제)",
                "persistentCookie", "remember-me (30일 후 삭제)"
        );
    }
}
