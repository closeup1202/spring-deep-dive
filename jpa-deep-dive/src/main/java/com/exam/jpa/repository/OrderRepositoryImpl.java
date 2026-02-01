package com.exam.jpa.repository;

import com.exam.jpa.dto.OrderDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.exam.jpa.domain.QOrder.order;
import static com.exam.jpa.domain.QOrderItem.orderItem;

@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OrderDto> searchOrders(String orderNumberCond, String productNameCond) {
        return queryFactory
                .select(Projections.constructor(OrderDto.class,
                        order.orderNumber,
                        orderItem.productName,
                        orderItem.price.multiply(orderItem.quantity).as("totalPrice"),
                        order.createdDate
                ))
                .from(order)
                .join(order.orderItems, orderItem) // 1:N 조인
                .where(
                        orderNumberEq(orderNumberCond),
                        productNameLike(productNameCond)
                )
                .fetch();
    }

    // 동적 쿼리를 위한 BooleanExpression 메서드들 (재사용 가능!)
    private BooleanExpression orderNumberEq(String orderNumber) {
        return orderNumber != null ? order.orderNumber.eq(orderNumber) : null;
    }

    private BooleanExpression productNameLike(String productName) {
        return productName != null ? orderItem.productName.contains(productName) : null;
    }
}
