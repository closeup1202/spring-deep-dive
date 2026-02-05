package com.project.curve.spring.outbox.persistence.jpa.adapter;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import com.project.curve.spring.outbox.persistence.jpa.entity.OutboxEventJpaEntity;
import com.project.curve.spring.outbox.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA implementation of OutboxEventRepository (Hexagonal Architecture Adapter).
 * <p>
 * Handles conversion between core domain models and JPA entities.
 *
 * @see OutboxEventRepository
 * @see OutboxEventJpaRepository
 */
@Component
@Transactional
@RequiredArgsConstructor
public class JpaOutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public void save(OutboxEvent event) {
        OutboxEventJpaEntity entity = jpaRepository.findById(event.getEventId())
                .map(existing -> {
                    existing.updateFromDomain(event);
                    return existing;
                })
                .orElseGet(() -> OutboxEventJpaEntity.fromDomain(event));

        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEvent> findById(String eventId) {
        return jpaRepository.findById(eventId)
                .map(OutboxEventJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return jpaRepository.findByStatusOrderByOccurredAtAsc(status, pageRequest)
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<OutboxEvent> findPendingForProcessing(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        // Query only events scheduled for retry before current time (nextRetryAt <= now)
        return jpaRepository.findByStatusAndNextRetryAtLessThanEqualForUpdateSkipLocked(
                        OutboxStatus.PENDING,
                        Instant.now(),
                        pageRequest
                )
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        return jpaRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                        aggregateType, aggregateId)
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String eventId) {
        jpaRepository.deleteById(eventId);
    }

    @Override
    public int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<String> idsToDelete = jpaRepository.findIdsByStatusAndOccurredAtBefore(status, before, pageRequest);

        if (idsToDelete.isEmpty()) {
            return 0;
        }

        return jpaRepository.deleteByEventIds(idsToDelete);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OutboxStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
