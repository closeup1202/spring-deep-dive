package com.exam.sessioncookie.session.basic;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @SessionAttribute 어노테이션 사용법
 * - Spring이 제공하는 편리한 세션 접근 방법
 * - 메서드 파라미터에서 바로 세션 값을 받을 수 있음
 */
@Slf4j
@RestController
@RequestMapping("/api/session/attribute")
public class SessionAttributeController {

    /**
     * 1. @SessionAttribute로 세션 값 읽기
     * required=false: 세션에 값이 없어도 에러가 발생하지 않음
     */
    @GetMapping("/user")
    public String getUser(@SessionAttribute(value = "username", required = false) String username) {
        if (username == null) {
            return "세션에 사용자 정보가 없습니다.";
        }
        log.info("Retrieved username from session: {}", username);
        return "현재 사용자: " + username;
    }

    /**
     * 2. @SessionAttribute로 복잡한 객체 읽기
     */
    @GetMapping("/cart")
    public String getCart(@SessionAttribute(value = "cart", required = false) ShoppingCart cart) {
        if (cart == null) {
            return "장바구니가 비어있습니다.";
        }
        log.info("Retrieved cart from session: {}", cart);
        return "장바구니 상품 수: " + cart.getItemCount();
    }

    @Data
    public static class ShoppingCart {
        private int itemCount;
        private double totalPrice;
    }
}
