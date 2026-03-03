package com.exam.sessioncookie.cookie.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @CookieValue 어노테이션 사용법
 * - Spring이 제공하는 편리한 쿠키 접근 방법
 * - 메서드 파라미터에서 바로 쿠키 값을 받을 수 있음
 */
@Slf4j
@RestController
@RequestMapping("/api/cookie/value")
public class CookieValueController {

    /**
     * 1. @CookieValue로 쿠키 값 읽기
     * required=false: 쿠키가 없어도 에러 발생하지 않음
     */
    @GetMapping("/user")
    public Map<String, Object> getUser(@CookieValue(value = "username", required = false) String username) {
        if (username == null) {
            return Map.of("message", "username 쿠키가 없습니다.");
        }

        log.info("Retrieved username from cookie: {}", username);

        return Map.of(
                "username", username,
                "message", "쿠키에서 사용자 이름을 조회했습니다."
        );
    }

    /**
     * 2. @CookieValue with default value
     */
    @GetMapping("/theme")
    public Map<String, Object> getTheme(
            @CookieValue(value = "theme", defaultValue = "light") String theme) {

        log.info("Retrieved theme from cookie: {}", theme);

        return Map.of(
                "theme", theme,
                "message", "쿠키가 없으면 기본값(light)이 사용됩니다."
        );
    }

    /**
     * 3. 여러 쿠키 값 동시 읽기
     */
    @GetMapping("/preferences")
    public Map<String, Object> getPreferences(
            @CookieValue(value = "language", defaultValue = "en") String language,
            @CookieValue(value = "theme", defaultValue = "light") String theme,
            @CookieValue(value = "fontSize", defaultValue = "14") String fontSize) {

        log.info("User preferences - language: {}, theme: {}, fontSize: {}", language, theme, fontSize);

        return Map.of(
                "language", language,
                "theme", theme,
                "fontSize", fontSize
        );
    }

    /**
     * 4. 쿠키 값을 비즈니스 로직에 활용
     */
    @GetMapping("/personalized-message")
    public Map<String, Object> getPersonalizedMessage(
            @CookieValue(value = "username", required = false) String username,
            @CookieValue(value = "visitCount", defaultValue = "0") int visitCount) {

        visitCount++;

        String message;
        if (username != null) {
            if (visitCount == 1) {
                message = "환영합니다, " + username + "님! 첫 방문이시네요.";
            } else {
                message = "다시 오신 것을 환영합니다, " + username + "님! (" + visitCount + "번째 방문)";
            }
        } else {
            message = "익명 사용자입니다. 로그인 해주세요.";
        }

        log.info("Personalized message generated: {}", message);

        return Map.of(
                "message", message,
                "visitCount", visitCount
        );
    }
}
