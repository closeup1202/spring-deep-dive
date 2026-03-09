package com.exam.spel;

import com.exam.spel.domain.Order;
import com.exam.spel.domain.User;
import com.exam.spel.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SpelAopTest {

    @Autowired
    private OrderService orderService;

    // --- @RequireRole 테스트 ---

    @Test
    @DisplayName("ADMIN은 주문 삭제 가능")
    void requireRole_admin_허용() {
        User admin = new User("관리자", "ADMIN");

        // SecurityException 없이 실행되어야 함
        orderService.deleteOrder(admin, 1L);
    }

    @Test
    @DisplayName("ADMIN이 아닌 경우 주문 삭제 시 SecurityException 발생")
    void requireRole_일반유저_차단() {
        User user = new User("홍길동", "USER");

        assertThatThrownBy(() -> orderService.deleteOrder(user, 1L))
                .isInstanceOf(SecurityException.class)
                .hasMessage("관리자만 주문을 삭제할 수 있습니다.");
    }

    // --- @AuditLog 테스트 ---

    @Test
    @DisplayName("주문 생성 후 AuditLog가 출력되고 반환값이 정상이어야 함")
    void auditLog_주문생성_로그출력() {
        User user = new User("홍길동", "USER");

        Order order = orderService.createOrder(user, 50000);

        // AuditLog는 로그 출력이 목적이므로 반환값 정상 여부만 검증
        assertThat(order.amount()).isEqualTo(50000);
        assertThat(order.userName()).isEqualTo("홍길동");
        // 실행 시 콘솔에서 [AUDIT] 주문 생성 - 사용자: 홍길동, 금액: 50000원 확인 가능
    }

    // --- 복합 조건 테스트 ---

    @Test
    @DisplayName("ADMIN은 다른 사람 주문도 조회 가능")
    void requireRole_복합조건_admin_허용() {
        User admin = new User("관리자", "ADMIN");

        Order order = orderService.getOrder(admin, "홍길동", 1L);

        assertThat(order).isNotNull();
    }

    @Test
    @DisplayName("본인 주문은 조회 가능")
    void requireRole_복합조건_본인_허용() {
        User user = new User("홍길동", "USER");

        Order order = orderService.getOrder(user, "홍길동", 1L);

        assertThat(order).isNotNull();
    }

    @Test
    @DisplayName("다른 사람 주문은 USER가 조회 불가")
    void requireRole_복합조건_타인_차단() {
        User user = new User("홍길동", "USER");

        assertThatThrownBy(() -> orderService.getOrder(user, "김철수", 1L))
                .isInstanceOf(SecurityException.class)
                .hasMessage("본인 또는 관리자만 조회할 수 있습니다.");
    }
}
