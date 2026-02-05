package com.exam.jpalocking.adapter.jpa;

import com.exam.jpalocking.adapter.jpa.entity.OutboxEventEntity;
import com.exam.jpalocking.adapter.jpa.repository.OutboxEventJpaRepository;
import com.exam.jpalocking.domain.OutboxEvent;
import com.exam.jpalocking.port.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaOutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public void save(OutboxEvent event) {
        jpaRepository.save(OutboxEventEntity.fromDomain(event));
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return jpaRepository.findById(id).map(OutboxEventEntity::toDomain);
    }

    @Override
    @Transactional
    public List<OutboxEvent> findPendingEventsForProcessing(int limit) {
        return jpaRepository.findPendingEventsSkipLocked(PageRequest.of(0, limit))
                .stream()
                .map(OutboxEventEntity::toDomain)
                .collect(Collectors.toList());
    }
}
