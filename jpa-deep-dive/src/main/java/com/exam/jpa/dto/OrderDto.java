package com.exam.jpa.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class OrderDto {
    private String orderNumber;
    private String productName; // 대표 상품명 하나만 가져온다고 가정
    private int totalPrice;
    private LocalDateTime orderDate;

    // @QueryProjection을 쓰면 Q-Type이 생성되어 더 안전하지만, 
    // 여기서는 Projections.fields/constructor 방식을 보여주기 위해 일반 생성자 사용
    public OrderDto(String orderNumber, String productName, int totalPrice, LocalDateTime orderDate) {
        this.orderNumber = orderNumber;
        this.productName = productName;
        this.totalPrice = totalPrice;
        this.orderDate = orderDate;
    }
}
