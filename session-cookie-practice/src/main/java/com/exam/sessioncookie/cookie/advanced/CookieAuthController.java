package com.exam.sessioncookie.cookie.advanced;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 쿠키 기반 인증 시스템
 * - Remember-Me 기능
 * - 자동 로그인 토큰
 * - 쿠키 기반 인증 vs 세션 기반 인증
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/auth")
@RequiredArgsConstructor
public class CookieAuthController {

    // 실무에서는 Redis나 DB에 저장
    private final Map<String, TokenData> tokenStore = new ConcurrentHashMap<>();

    /**
     * 1. 로그인 (Remember-Me 옵션)
     */
    @PostMapping("/login")
    public Map<String, Object> login(
            HttpServletResponse response,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "false") boolean rememberMe) {

        // 실제로는 DB에서 사용자 검증
        if (!"admin".equals(username) || !"1234".equals(password)) {
            return Map.of("success", false, "message", "로그인 실패");
        }

        // 인증 토큰 생성
        String token = UUID.randomUUID().toString();
        TokenData tokenData = new TokenData(username, System.currentTimeMillis());
        tokenStore.put(token, tokenData);

        // 쿠키 생성
        Cookie authCookie = new Cookie("authToken", token);
        authCookie.setPath("/");
        authCookie.setHttpOnly(true);
        authCookie.setSecure(true);

        if (rememberMe) {
            authCookie.setMaxAge(30 * 24 * 60 * 60); // 30일
            log.info("Remember-Me login: username={}, token={}", username, token);
        } else {
            authCookie.setMaxAge(-1); // 세션 쿠키
            log.info("Session login: username={}, token={}", username, token);
        }

        response.addCookie(authCookie);

        return Map.of(
                "success", true,
                "message", "로그인 성공",
                "username", username,
                "rememberMe", rememberMe
        );
    }

    /**
     * 2. 자동 로그인 확인
     */
    @GetMapping("/check")
    public Map<String, Object> checkAuth(
            @CookieValue(value = "authToken", required = false) String token) {

        if (token == null) {
            return Map.of("authenticated", false, "message", "인증 토큰이 없습니다.");
        }

        TokenData tokenData = tokenStore.get(token);
        if (tokenData == null) {
            return Map.of("authenticated", false, "message", "유효하지 않은 토큰입니다.");
        }

        // 토큰 유효기간 검증 (30일)
        long now = System.currentTimeMillis();
        long thirtyDays = 30L * 24 * 60 * 60 * 1000;
        if (now - tokenData.getCreatedAt() > thirtyDays) {
            tokenStore.remove(token);
            return Map.of("authenticated", false, "message", "토큰이 만료되었습니다.");
        }

        log.info("Auto-login successful: username={}", tokenData.getUsername());

        return Map.of(
                "authenticated", true,
                "username", tokenData.getUsername(),
                "message", "자동 로그인 성공"
        );
    }

    /**
     * 3. 로그아웃
     */
    @PostMapping("/logout")
    public Map<String, String> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Arrays.stream(cookies)
                    .filter(cookie -> "authToken".equals(cookie.getName()))
                    .findFirst()
                    .ifPresent(cookie -> {
                        // 토큰 스토어에서 제거
                        tokenStore.remove(cookie.getValue());

                        // 쿠키 삭제
                        Cookie deleteCookie = new Cookie("authToken", null);
                        deleteCookie.setMaxAge(0);
                        deleteCookie.setPath("/");
                        response.addCookie(deleteCookie);

                        log.info("Logout successful: token={}", cookie.getValue());
                    });
        }

        return Map.of("message", "로그아웃 되었습니다.");
    }

    /**
     * 4. 모든 디바이스에서 로그아웃
     */
    @PostMapping("/logout-all")
    public Map<String, Object> logoutAllDevices(@RequestParam String username) {
        long count = tokenStore.entrySet().stream()
                .filter(entry -> entry.getValue().getUsername().equals(username))
                .peek(entry -> log.info("Removing token: {}", entry.getKey()))
                .count();

        tokenStore.entrySet().removeIf(entry -> entry.getValue().getUsername().equals(username));

        return Map.of(
                "message", "모든 디바이스에서 로그아웃되었습니다.",
                "revokedTokenCount", count
        );
    }

    /**
     * TokenData 내부 클래스
     */
    private static class TokenData {
        private final String username;
        private final long createdAt;

        public TokenData(String username, long createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }

        public String getUsername() {
            return username;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
}
