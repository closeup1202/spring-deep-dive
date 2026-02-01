package com.exam.jpa.repository;

import com.exam.jpa.dto.OrderDto;
import java.util.List;

public interface OrderRepositoryCustom {
    List<OrderDto> searchOrders(String orderNumber, String productName);
}
