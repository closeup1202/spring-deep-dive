package com.exam.sessioncookie.cookie.advanced;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 쿠키 보안 실무 패턴
 * - XSS 방어 (HttpOnly)
 * - CSRF 방어 (SameSite)
 * - 중간자 공격 방어 (Secure)
 * - 쿠키 Prefix (__Secure-, __Host-)
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/security")
public class CookieSecurityController {

    /**
     * 1. 보안 쿠키 생성 (모든 보안 속성 적용)
     */
    @PostMapping("/create-secure")
    public Map<String, Object> createSecureCookie(HttpServletResponse response) {
        // Set-Cookie 헤더를 직접 설정하여 모든 속성 제어
        String cookieHeader = "__Secure-sessionId=abc123xyz; " +
                "Max-Age=3600; " +
                "Path=/; " +
                "Secure; " +
                "HttpOnly; " +
                "SameSite=Strict";

        response.addHeader("Set-Cookie", cookieHeader);

        log.info("Secure cookie created with all security attributes");

        return Map.of(
                "message", "모든 보안 속성이 적용된 쿠키가 생성되었습니다.",
                "attributes", Map.of(
                        "Secure", "HTTPS에서만 전송",
                        "HttpOnly", "JavaScript 접근 차단",
                        "SameSite", "CSRF 공격 방어",
                        "__Secure-", "Secure 속성 강제"
                )
        );
    }

    /**
     * 2. __Host- Prefix 쿠키 (가장 강력한 보안)
     * - Path는 반드시 /
     * - Domain 속성 없음
     * - Secure 필수
     */
    @PostMapping("/create-host-prefix")
    public Map<String, Object> createHostPrefixCookie(HttpServletResponse response) {
        String cookieHeader = "__Host-token=xyz789abc; " +
                "Max-Age=3600; " +
                "Path=/; " +
                "Secure; " +
                "HttpOnly; " +
                "SameSite=Strict";

        response.addHeader("Set-Cookie", cookieHeader);

        log.info("__Host- prefix cookie created");

        return Map.of(
                "message", "__Host- Prefix 쿠키가 생성되었습니다.",
                "security", Map.of(
                        "prefix", "__Host-",
                        "domain", "설정 불가 (현재 호스트만)",
                        "path", "/ 고정",
                        "secure", "필수",
                        "protection", "서브도메인 공격 방어"
                )
        );
    }

    /**
     * 3. SameSite 속성 비교
     */
    @PostMapping("/create-samesite/{mode}")
    public Map<String, Object> createCookieWithSameSite(
            HttpServletResponse response,
            @PathVariable String mode) {

        if (!Arrays.asList("Strict", "Lax", "None").contains(mode)) {
            return Map.of("error", "mode는 Strict, Lax, None 중 하나여야 합니다.");
        }

        String cookieHeader = "sameSiteTest=value123; " +
                "Max-Age=3600; " +
                "Path=/; " +
                "HttpOnly; " +
                "Secure; " +
                "SameSite=" + mode;

        response.addHeader("Set-Cookie", cookieHeader);

        log.info("Cookie with SameSite={} created", mode);

        Map<String, String> explanation = new HashMap<>();
        explanation.put("Strict", "동일한 사이트에서만 쿠키 전송 (가장 안전)");
        explanation.put("Lax", "안전한 HTTP 메서드(GET)로 이동 시 전송");
        explanation.put("None", "모든 요청에 전송 (Secure 필수, 가장 취약)");

        return Map.of(
                "message", "SameSite=" + mode + " 쿠키가 생성되었습니다.",
                "mode", mode,
                "explanation", explanation.get(mode)
        );
    }

    /**
     * 4. CSRF 토큰 쿠키 생성
     */
    @PostMapping("/create-csrf-token")
    public Map<String, Object> createCsrfToken(HttpServletResponse response) {
        String csrfToken = java.util.UUID.randomUUID().toString();

        // CSRF 토큰은 JavaScript에서 읽을 수 있어야 함 (HttpOnly X)
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
        csrfCookie.setMaxAge(3600);
        csrfCookie.setPath("/");
        csrfCookie.setSecure(true);
        // HttpOnly를 false로 설정 (기본값)

        response.addCookie(csrfCookie);

        log.info("CSRF token cookie created: {}", csrfToken);

        return Map.of(
                "message", "CSRF 토큰 쿠키가 생성되었습니다.",
                "csrfToken", csrfToken,
                "usage", "POST/PUT/DELETE 요청 시 X-XSRF-TOKEN 헤더에 이 값을 포함하세요.",
                "note", "JavaScript에서 읽을 수 있도록 HttpOnly가 false입니다."
        );
    }

    /**
     * 5. CSRF 토큰 검증
     */
    @PostMapping("/verify-csrf")
    public Map<String, Object> verifyCsrf(
            HttpServletRequest request,
            @RequestHeader(value = "X-XSRF-TOKEN", required = false) String csrfHeader) {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Map.of("valid", false, "message", "쿠키가 없습니다.");
        }

        String csrfCookie = Arrays.stream(cookies)
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (csrfCookie == null) {
            return Map.of("valid", false, "message", "CSRF 토큰 쿠키가 없습니다.");
        }

        if (csrfHeader == null) {
            return Map.of("valid", false, "message", "X-XSRF-TOKEN 헤더가 없습니다.");
        }

        boolean isValid = csrfCookie.equals(csrfHeader);

        if (isValid) {
            log.info("CSRF token verified successfully");
        } else {
            log.warn("CSRF token verification failed: cookie={}, header={}", csrfCookie, csrfHeader);
        }

        return Map.of(
                "valid", isValid,
                "message", isValid ? "CSRF 토큰 검증 성공" : "CSRF 토큰 불일치",
                "cookieToken", csrfCookie,
                "headerToken", csrfHeader
        );
    }

    /**
     * 6. 쿠키 보안 체크리스트
     */
    @GetMapping("/security-checklist/{cookieName}")
    public Map<String, Object> checkCookieSecurity(
            HttpServletRequest request,
            @PathVariable String cookieName) {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Map.of("message", "쿠키가 없습니다.");
        }

        Cookie cookie = Arrays.stream(cookies)
                .filter(c -> c.getName().equals(cookieName))
                .findFirst()
                .orElse(null);

        if (cookie == null) {
            return Map.of("message", "해당 쿠키를 찾을 수 없습니다.");
        }

        Map<String, Object> securityCheck = new HashMap<>();
        securityCheck.put("name", cookie.getName());
        securityCheck.put("httpOnly", cookie.isHttpOnly());
        securityCheck.put("secure", cookie.getSecure());
        securityCheck.put("path", cookie.getPath());
        securityCheck.put("maxAge", cookie.getMaxAge());

        // 보안 등급 평가
        int securityScore = 0;
        if (cookie.isHttpOnly()) securityScore += 30;
        if (cookie.getSecure()) securityScore += 30;
        if (cookie.getName().startsWith("__Secure-")) securityScore += 20;
        if (cookie.getName().startsWith("__Host-")) securityScore += 20;

        String grade;
        if (securityScore >= 80) grade = "A (매우 안전)";
        else if (securityScore >= 60) grade = "B (안전)";
        else if (securityScore >= 40) grade = "C (보통)";
        else grade = "D (취약)";

        securityCheck.put("securityScore", securityScore);
        securityCheck.put("grade", grade);

        log.info("Security check: cookie={}, score={}, grade={}", cookieName, securityScore, grade);

        return securityCheck;
    }

    /**
     * 7. 모든 쿠키 보안 감사
     */
    @GetMapping("/audit")
    public Map<String, Object> auditAllCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Map.of("message", "쿠키가 없습니다.");
        }

        Map<String, Map<String, Object>> audit = new HashMap<>();
        int totalScore = 0;

        for (Cookie cookie : cookies) {
            Map<String, Object> cookieInfo = new HashMap<>();
            cookieInfo.put("httpOnly", cookie.isHttpOnly());
            cookieInfo.put("secure", cookie.getSecure());
            cookieInfo.put("path", cookie.getPath());

            int score = 0;
            if (cookie.isHttpOnly()) score += 30;
            if (cookie.getSecure()) score += 30;
            if (cookie.getName().startsWith("__Secure-") || cookie.getName().startsWith("__Host-")) score += 40;

            cookieInfo.put("securityScore", score);
            totalScore += score;

            audit.put(cookie.getName(), cookieInfo);
        }

        int averageScore = cookies.length > 0 ? totalScore / cookies.length : 0;

        log.info("Cookie security audit completed: totalCookies={}, averageScore={}",
                cookies.length, averageScore);

        return Map.of(
                "totalCookies", cookies.length,
                "averageSecurityScore", averageScore,
                "cookies", audit
        );
    }
}
