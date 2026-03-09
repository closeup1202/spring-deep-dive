package com.exam.spel.service;

import com.exam.spel.annotation.AuditLog;
import com.exam.spel.annotation.RequireRole;
import com.exam.spel.domain.Order;
import com.exam.spel.domain.User;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    /**
     * ADMIN 역할만 주문 삭제 가능
     * SpEL: #user.role == 'ADMIN'
     * → #user 는 파라미터 user를 참조, .role 은 User 레코드의 role() 호출
     */
    @RequireRole(value = "#user.role == 'ADMIN'", message = "관리자만 주문을 삭제할 수 있습니다.")
    public void deleteOrder(User user, Long orderId) {
        // delete logic
    }

    /**
     * 주문 생성 후 AuditLog
     * SpEL: #user.name → 파라미터 참조, #result.amount → 반환값 참조
     */
    @AuditLog("'주문 생성 - 사용자: ' + #user.name + ', 금액: ' + #result.amount + '원'")
    public Order createOrder(User user, int amount) {
        return new Order(1L, user.name(), amount);
    }

    /**
     * 복합 조건: ADMIN이거나 본인 주문인 경우에만 조회 허용
     * SpEL: T(연산자), #userId 와 #requesterId 비교
     */
    @RequireRole(
        value = "#requester.role == 'ADMIN' or #requester.name == #targetName",
        message = "본인 또는 관리자만 조회할 수 있습니다."
    )
    public Order getOrder(User requester, String targetName, Long orderId) {
        return new Order(orderId, targetName, 10000);
    }
}
