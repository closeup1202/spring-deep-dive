package com.exam.jpalocking.port;

import com.exam.jpalocking.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository {
    void save(OutboxEvent event);
    Optional<OutboxEvent> findById(Long id);
    List<OutboxEvent> findPendingEventsForProcessing(int limit);
}
