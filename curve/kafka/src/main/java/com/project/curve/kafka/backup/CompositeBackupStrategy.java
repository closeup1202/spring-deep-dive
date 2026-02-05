package com.project.curve.kafka.backup;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Composite strategy that tries multiple backup strategies in order.
 * <p>
 * For example, try S3 first, and if it fails, fall back to local file.
 */
@Slf4j
public class CompositeBackupStrategy implements EventBackupStrategy {

    private final List<EventBackupStrategy> strategies;

    public CompositeBackupStrategy(List<EventBackupStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public void backup(String eventId, Object payload, Throwable cause) {
        for (EventBackupStrategy strategy : strategies) {
            try {
                strategy.backup(eventId, payload, cause);
                return; // Success, stop trying
            } catch (Exception e) {
                log.warn("Backup strategy {} failed for eventId={}. Trying next strategy.",
                        strategy.getClass().getSimpleName(), eventId, e);
            }
        }
        log.error("All backup strategies failed for eventId={}. Event is permanently lost.", eventId);
    }
}
