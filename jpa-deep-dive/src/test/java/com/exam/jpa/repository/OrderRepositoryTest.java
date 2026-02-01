package com.exam.jpa.repository;

import com.exam.jpa.config.QueryDslConfig;
import com.exam.jpa.domain.Order;
import com.exam.jpa.domain.OrderItem;
import com.exam.jpa.dto.OrderDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QueryDslConfig.class) // QueryDSL 설정 빈 로드
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("Cascade: Order만 저장해도 OrderItem까지 자동 저장된다")
    void cascadeTest() {
        // Given
        Order order = new Order("ORD-001");
        order.addOrderItem(new OrderItem("MacBook", 2000000, 1));
        order.addOrderItem(new OrderItem("Mouse", 50000, 2));

        // When
        orderRepository.save(order);
        em.flush();
        em.clear();

        // Then
        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(savedOrder.getOrderItems()).hasSize(2);
    }

    @Test
    @DisplayName("QueryDSL: 동적 쿼리로 주문 검색 및 DTO 조회")
    void queryDslTest() {
        // Given
        Order order1 = new Order("ORD-A");
        order1.addOrderItem(new OrderItem("Apple", 1000, 5));
        orderRepository.save(order1);

        Order order2 = new Order("ORD-B");
        order2.addOrderItem(new OrderItem("Banana", 2000, 3));
        orderRepository.save(order2);

        // When 1: 상품명에 "App"이 들어가는 주문 검색
        List<OrderDto> result1 = orderRepository.searchOrders(null, "App");
        assertThat(result1).hasSize(1);
        assertThat(result1.get(0).getOrderNumber()).isEqualTo("ORD-A");
        assertThat(result1.get(0).getTotalPrice()).isEqualTo(5000); // 1000 * 5

        // When 2: 주문번호가 "ORD-B"인 주문 검색
        List<OrderDto> result2 = orderRepository.searchOrders("ORD-B", null);
        assertThat(result2).hasSize(1);
        assertThat(result2.get(0).getProductName()).isEqualTo("Banana");
    }
}
