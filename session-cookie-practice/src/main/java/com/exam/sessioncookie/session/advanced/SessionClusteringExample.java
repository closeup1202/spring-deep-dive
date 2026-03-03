package com.exam.sessioncookie.session.advanced;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 세션 클러스터링 시뮬레이션
 * - 여러 서버 인스턴스에서 세션 공유 확인
 * - 로드 밸런싱 환경에서의 세션 일관성
 */
@Slf4j
@RestController
@RequestMapping("/api/session/clustering")
@RequiredArgsConstructor
public class SessionClusteringExample {

    private final AtomicInteger requestCount = new AtomicInteger(0);

    /**
     * 1. 서버 정보와 함께 세션 카운터 증가
     * - 로드 밸런서를 통해 여러 서버로 요청이 분산되어도 세션은 공유됨
     */
    @PostMapping("/increment")
    public Map<String, Object> incrementCounter(HttpServletRequest request) throws UnknownHostException {
        HttpSession session = request.getSession(true);

        // 세션에 저장된 카운터 가져오기
        Integer counter = (Integer) session.getAttribute("counter");
        if (counter == null) {
            counter = 0;
        }
        counter++;
        session.setAttribute("counter", counter);

        // 이 요청을 처리한 서버 정보
        String serverInfo = InetAddress.getLocalHost().getHostName();
        int localRequestCount = requestCount.incrementAndGet();

        log.info("Request handled by: {}, Session counter: {}, Local requests: {}",
                serverInfo, counter, localRequestCount);

        return Map.of(
                "sessionId", session.getId(),
                "counter", counter,
                "serverInfo", serverInfo,
                "localRequestCount", localRequestCount,
                "message", "세션 카운터가 증가했습니다. (Redis 기반 클러스터링)"
        );
    }

    /**
     * 2. 세션 기반 장바구니 (분산 환경)
     */
    @PostMapping("/cart/add")
    public Map<String, Object> addToCart(
            HttpServletRequest request,
            @RequestParam String itemName,
            @RequestParam int quantity) throws UnknownHostException {

        HttpSession session = request.getSession(true);

        @SuppressWarnings("unchecked")
        Map<String, CartItem> cart = (Map<String, CartItem>) session.getAttribute("shoppingCart");
        if (cart == null) {
            cart = new HashMap<>();
        }

        CartItem item = cart.getOrDefault(itemName, new CartItem(itemName, 0));
        item.setQuantity(item.getQuantity() + quantity);
        cart.put(itemName, item);

        session.setAttribute("shoppingCart", cart);

        String serverInfo = InetAddress.getLocalHost().getHostName();

        log.info("Item added to cart: {} x{} on server: {}", itemName, quantity, serverInfo);

        return Map.of(
                "cart", cart,
                "serverInfo", serverInfo,
                "message", "상품이 장바구니에 추가되었습니다."
        );
    }

    /**
     * 3. 장바구니 조회
     */
    @GetMapping("/cart")
    public Map<String, Object> getCart(HttpServletRequest request) throws UnknownHostException {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("message", "세션이 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, CartItem> cart = (Map<String, CartItem>) session.getAttribute("shoppingCart");

        String serverInfo = InetAddress.getLocalHost().getHostName();

        log.info("Cart retrieved on server: {}", serverInfo);

        return Map.of(
                "cart", cart != null ? cart : Map.of(),
                "serverInfo", serverInfo,
                "sessionId", session.getId()
        );
    }

    /**
     * 4. 세션 복제 지연 시뮬레이션
     * - 세션 데이터를 수정한 후 즉시 다른 서버에서 조회하면?
     */
    @PostMapping("/test-replication")
    public Map<String, Object> testReplication(HttpServletRequest request) throws UnknownHostException {
        HttpSession session = request.getSession(true);

        long timestamp = System.currentTimeMillis();
        session.setAttribute("lastUpdate", timestamp);

        String serverInfo = InetAddress.getLocalHost().getHostName();

        log.info("Session updated at {} on server: {}", timestamp, serverInfo);

        return Map.of(
                "sessionId", session.getId(),
                "lastUpdate", timestamp,
                "serverInfo", serverInfo,
                "message", "다른 서버에서 즉시 이 값을 조회해보세요. (Redis는 지연이 거의 없음)"
        );
    }

    @Data
    public static class CartItem {
        private String name;
        private int quantity;

        public CartItem(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }
}
