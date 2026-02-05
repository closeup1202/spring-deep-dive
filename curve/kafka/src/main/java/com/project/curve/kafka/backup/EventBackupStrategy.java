package com.project.curve.kafka.backup;

/**
 * Strategy interface for backing up events when DLQ transmission fails.
 */
public interface EventBackupStrategy {
    /**
     * Backup the failed event.
     *
     * @param eventId       The unique identifier of the event
     * @param payload       The original event payload
     * @param cause         The exception that caused the failure
     */
    void backup(String eventId, Object payload, Throwable cause);
}
