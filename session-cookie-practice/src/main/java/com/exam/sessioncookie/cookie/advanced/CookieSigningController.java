package com.exam.sessioncookie.cookie.advanced;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * 쿠키 서명 및 검증 (Cookie Signing)
 * - 쿠키 변조 방지
 * - HMAC 서명
 * - 쿠키 무결성 검증
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/signing")
public class CookieSigningController {

    private static final String SECRET_KEY = "my-super-secret-key-12345"; // 실무에서는 환경변수
    private static final String SIGNATURE_DELIMITER = ".";

    /**
     * 1. 서명된 쿠키 생성
     * 형식: value.signature
     */
    @PostMapping("/create-signed")
    public Map<String, Object> createSignedCookie(
            HttpServletResponse response,
            @RequestParam String name,
            @RequestParam String value) throws Exception {

        // HMAC 서명 생성
        String signature = generateHmacSignature(value);
        String signedValue = value + SIGNATURE_DELIMITER + signature;

        // URL 인코딩
        String encodedValue = URLEncoder.encode(signedValue, StandardCharsets.UTF_8);

        Cookie cookie = new Cookie(name, encodedValue);
        cookie.setMaxAge(3600);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        response.addCookie(cookie);

        log.info("Signed cookie created: name={}, value={}, signature={}", name, value, signature);

        return Map.of(
                "message", "서명된 쿠키가 생성되었습니다.",
                "name", name,
                "originalValue", value,
                "signature", signature,
                "signedValue", signedValue
        );
    }

    /**
     * 2. 서명 검증
     */
    @GetMapping("/verify/{cookieName}")
    public Map<String, Object> verifyCookie(
            HttpServletRequest request,
            @PathVariable String cookieName) throws Exception {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Map.of("valid", false, "message", "쿠키가 없습니다.");
        }

        Cookie cookie = Arrays.stream(cookies)
                .filter(c -> c.getName().equals(cookieName))
                .findFirst()
                .orElse(null);

        if (cookie == null) {
            return Map.of("valid", false, "message", "해당 쿠키를 찾을 수 없습니다.");
        }

        // URL 디코딩
        String decodedValue = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

        // value.signature 분리
        String[] parts = decodedValue.split("\\" + SIGNATURE_DELIMITER);
        if (parts.length != 2) {
            return Map.of("valid", false, "message", "잘못된 쿠키 형식입니다.");
        }

        String originalValue = parts[0];
        String providedSignature = parts[1];

        // 서명 재생성 및 비교
        String expectedSignature = generateHmacSignature(originalValue);
        boolean isValid = expectedSignature.equals(providedSignature);

        if (isValid) {
            log.info("Cookie signature verified: name={}, value={}", cookieName, originalValue);
        } else {
            log.warn("Cookie signature verification failed: name={}, tampering detected", cookieName);
        }

        return Map.of(
                "valid", isValid,
                "cookieName", cookieName,
                "value", originalValue,
                "message", isValid ? "쿠키 서명이 유효합니다." : "쿠키가 변조되었습니다!"
        );
    }

    /**
     * 3. 서명된 쿠키 값 읽기 (검증 후)
     */
    @GetMapping("/read-signed/{cookieName}")
    public Map<String, Object> readSignedCookie(
            HttpServletRequest request,
            @PathVariable String cookieName) throws Exception {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Map.of("success", false, "message", "쿠키가 없습니다.");
        }

        Cookie cookie = Arrays.stream(cookies)
                .filter(c -> c.getName().equals(cookieName))
                .findFirst()
                .orElse(null);

        if (cookie == null) {
            return Map.of("success", false, "message", "해당 쿠키를 찾을 수 없습니다.");
        }

        // URL 디코딩
        String decodedValue = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        String[] parts = decodedValue.split("\\" + SIGNATURE_DELIMITER);

        if (parts.length != 2) {
            return Map.of("success", false, "message", "잘못된 쿠키 형식입니다.");
        }

        String originalValue = parts[0];
        String providedSignature = parts[1];
        String expectedSignature = generateHmacSignature(originalValue);

        if (!expectedSignature.equals(providedSignature)) {
            return Map.of("success", false, "message", "쿠키가 변조되었습니다!");
        }

        return Map.of(
                "success", true,
                "cookieName", cookieName,
                "value", originalValue,
                "message", "쿠키 값이 안전하게 읽혔습니다."
        );
    }

    /**
     * 4. 변조된 쿠키 생성 (테스트용)
     */
    @PostMapping("/create-tampered")
    public Map<String, String> createTamperedCookie(HttpServletResponse response) throws Exception {
        String originalValue = "user123";
        String signature = generateHmacSignature(originalValue);

        // 의도적으로 값을 변조
        String tamperedValue = "admin999" + SIGNATURE_DELIMITER + signature;
        String encodedValue = URLEncoder.encode(tamperedValue, StandardCharsets.UTF_8);

        Cookie cookie = new Cookie("tamperedCookie", encodedValue);
        cookie.setMaxAge(3600);
        cookie.setPath("/");

        response.addCookie(cookie);

        log.warn("Tampered cookie created for testing");

        return Map.of(
                "message", "변조된 쿠키가 생성되었습니다. (테스트용)",
                "warning", "이 쿠키는 검증에 실패합니다."
        );
    }

    /**
     * HMAC-SHA256 서명 생성
     */
    private String generateHmacSignature(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);

        byte[] hmacBytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
}
