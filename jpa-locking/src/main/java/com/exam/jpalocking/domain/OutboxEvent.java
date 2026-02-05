package com.exam.jpalocking.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor
public class OutboxEvent {
    private Long id;
    private String payload;
    private String status; // PENDING, PROCESSED
    private Instant createdAt;

    public OutboxEvent(Long id, String payload, String status, Instant createdAt) {
        this.id = id;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void markAsProcessed() {
        this.status = "PROCESSED";
    }
}
