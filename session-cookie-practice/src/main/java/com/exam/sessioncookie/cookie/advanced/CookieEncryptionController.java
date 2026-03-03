package com.exam.sessioncookie.cookie.advanced;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * 쿠키 암호화 (Cookie Encryption)
 * - 민감한 정보를 쿠키에 저장할 때 사용
 * - AES 암호화/복호화
 * - 실무에서는 쿠키보다 세션이나 JWT 사용 권장
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/encryption")
public class CookieEncryptionController {

    // 실무에서는 환경변수나 Key Management Service 사용
    private static final String SECRET_KEY = "0123456789abcdef"; // 16 bytes for AES-128
    private static final String ALGORITHM = "AES";

    /**
     * 1. 암호화된 쿠키 생성
     */
    @PostMapping("/create-encrypted")
    public Map<String, Object> createEncryptedCookie(
            HttpServletResponse response,
            @RequestParam String name,
            @RequestParam String value) throws Exception {

        // AES 암호화
        String encryptedValue = encrypt(value);

        // URL 인코딩
        String encodedValue = URLEncoder.encode(encryptedValue, StandardCharsets.UTF_8);

        Cookie cookie = new Cookie(name, encodedValue);
        cookie.setMaxAge(3600);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);

        response.addCookie(cookie);

        log.info("Encrypted cookie created: name={}, originalValue={}, encryptedValue={}",
                name, value, encryptedValue);

        return Map.of(
                "message", "암호화된 쿠키가 생성되었습니다.",
                "name", name,
                "originalValue", value,
                "encryptedValue", encryptedValue,
                "note", "쿠키 값은 암호화되어 있어 브라우저에서 읽을 수 없습니다."
        );
    }

    /**
     * 2. 암호화된 쿠키 읽기 (복호화)
     */
    @GetMapping("/read-encrypted/{cookieName}")
    public Map<String, Object> readEncryptedCookie(
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

        try {
            // URL 디코딩
            String decodedValue = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);

            // AES 복호화
            String decryptedValue = decrypt(decodedValue);

            log.info("Encrypted cookie read: name={}, decryptedValue={}", cookieName, decryptedValue);

            return Map.of(
                    "success", true,
                    "cookieName", cookieName,
                    "value", decryptedValue,
                    "message", "쿠키가 성공적으로 복호화되었습니다."
            );
        } catch (Exception e) {
            log.error("Failed to decrypt cookie: {}", cookieName, e);
            return Map.of(
                    "success", false,
                    "message", "쿠키 복호화 실패: " + e.getMessage()
            );
        }
    }

    /**
     * 3. 민감한 사용자 정보를 암호화하여 저장
     */
    @PostMapping("/save-user-info")
    public Map<String, String> saveUserInfo(
            HttpServletResponse response,
            @RequestParam String userId,
            @RequestParam String email) throws Exception {

        // 민감한 정보를 JSON 형태로 결합
        String userInfo = userId + "|" + email;

        // 암호화
        String encryptedInfo = encrypt(userInfo);
        String encodedValue = URLEncoder.encode(encryptedInfo, StandardCharsets.UTF_8);

        Cookie cookie = new Cookie("userInfo", encodedValue);
        cookie.setMaxAge(3600);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);

        response.addCookie(cookie);

        log.info("User info saved in encrypted cookie: userId={}", userId);

        return Map.of(
                "message", "사용자 정보가 암호화되어 저장되었습니다.",
                "note", "실무에서는 민감한 정보를 쿠키에 저장하기보다 세션을 사용하세요."
        );
    }

    /**
     * 4. 저장된 사용자 정보 복호화하여 읽기
     */
    @GetMapping("/get-user-info")
    public Map<String, Object> getUserInfo(HttpServletRequest request) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Map.of("success", false, "message", "쿠키가 없습니다.");
        }

        Cookie cookie = Arrays.stream(cookies)
                .filter(c -> "userInfo".equals(c.getName()))
                .findFirst()
                .orElse(null);

        if (cookie == null) {
            return Map.of("success", false, "message", "사용자 정보 쿠키가 없습니다.");
        }

        try {
            String decodedValue = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
            String decryptedInfo = decrypt(decodedValue);

            String[] parts = decryptedInfo.split("\\|");
            String userId = parts[0];
            String email = parts[1];

            log.info("User info retrieved: userId={}", userId);

            return Map.of(
                    "success", true,
                    "userId", userId,
                    "email", email
            );
        } catch (Exception e) {
            log.error("Failed to read user info", e);
            return Map.of("success", false, "message", "사용자 정보 읽기 실패");
        }
    }

    /**
     * AES 암호화
     */
    private String encrypt(String value) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * AES 복호화
     */
    private String decrypt(String encryptedValue) throws Exception {
        SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);

        byte[] decodedBytes = Base64.getDecoder().decode(encryptedValue);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
