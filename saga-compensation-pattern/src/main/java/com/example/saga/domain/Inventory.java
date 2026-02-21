package com.example.saga.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String productId;

    private Integer availableQuantity;
    private Integer reservedQuantity;

    public Inventory(String productId, Integer initialQuantity) {
        this.productId = productId;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public boolean canReserve(Integer quantity) {
        return availableQuantity >= quantity;
    }

    public void reserve(Integer quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("재고 부족: 요청=" + quantity + ", 가용=" + availableQuantity);
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }

    public void releaseReservation(Integer quantity) {
        this.availableQuantity += quantity;
        this.reservedQuantity -= quantity;
    }

    public void confirmReservation(Integer quantity) {
        this.reservedQuantity -= quantity;
    }
}
