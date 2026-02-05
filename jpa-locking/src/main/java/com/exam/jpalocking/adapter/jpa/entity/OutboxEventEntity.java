package com.exam.jpalocking.adapter.jpa.entity;

import com.exam.jpalocking.domain.OutboxEvent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String payload;
    private String status;
    private Instant createdAt;

    public static OutboxEventEntity fromDomain(OutboxEvent domain) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = domain.getId();
        entity.payload = domain.getPayload();
        entity.status = domain.getStatus();
        entity.createdAt = domain.getCreatedAt();
        return entity;
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(id, payload, status, createdAt);
    }
}
