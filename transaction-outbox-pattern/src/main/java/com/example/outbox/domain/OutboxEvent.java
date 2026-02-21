package com.example.outbox.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId;
    private String aggregateType;
    private String eventType;

    @Column(length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public OutboxEvent(String aggregateId, String aggregateType, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.status = EventStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsProcessed() {
        this.status = EventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = EventStatus.FAILED;
    }

    public enum EventStatus {
        PENDING, PROCESSED, FAILED
    }
}
