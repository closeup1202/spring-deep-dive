package com.exam.jpa.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // order는 예약어인 경우가 많아 orders로 명명
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber;

    // ★ 실무 포인트: 부모(Order)가 자식(OrderItem)의 생명주기를 관리함 (Aggregate Root)
    // CascadeType.ALL: Order 저장/삭제 시 OrderItem도 같이 처리
    // orphanRemoval = true: 리스트에서 제거하면 DB에서도 DELETE 쿼리 나감
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public Order(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    // 연관관계 편의 메서드
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
}
