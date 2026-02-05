package com.project.curve.spring.outbox.publisher;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publisher that periodically publishes Outbox events to Kafka.
 * <p>
 * Core component of the Transactional Outbox Pattern.
 *
 * <h3>Operation</h3>
 * <ol>
 *   <li>Query PENDING events at fixed intervals (default 1 second)</li>
 *   <li>Attempt to publish queried events to Kafka</li>
 *   <li>On success, change to PUBLISHED status</li>
 *   <li>On failure, increment retry count; change to FAILED status when max retries exceeded</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <pre>
 * curve:
 *   outbox:
 *     enabled: true
 *     poll-interval-ms: 1000      # Polling interval
 *     batch-size: 100              # Number of events to process at once
 *     max-retries: 3               # Maximum retry count
 * </pre>
 *
 * @see OutboxEvent
 * @see OutboxEventRepository
 */
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final int maxRetries;
    private final int sendTimeoutSeconds;
    private final boolean cleanupEnabled;
    private final int retentionDays;
    private final boolean dynamicBatchingEnabled;
    private final boolean circuitBreakerEnabled;

    // Statistics and metrics
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    // Circuit Breaker state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;

    // Circuit Breaker configuration
    private static final int FAILURE_THRESHOLD = 5; // Open circuit after 5 consecutive failures
    private static final long CIRCUIT_OPEN_DURATION_MS = 60000L; // Attempt Half-Open after 1 minute
    private static final int HALF_OPEN_MAX_ATTEMPTS = 3; // Maximum attempts in Half-Open state

    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            String topic,
            int batchSize,
            int maxRetries,
            int sendTimeoutSeconds,
            boolean cleanupEnabled,
            int retentionDays,
            boolean dynamicBatchingEnabled,
            boolean circuitBreakerEnabled
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.cleanupEnabled = cleanupEnabled;
        this.retentionDays = retentionDays;
        this.dynamicBatchingEnabled = dynamicBatchingEnabled;
        this.circuitBreakerEnabled = circuitBreakerEnabled;

        log.info("OutboxEventPublisher initialized: topic={}, batchSize={}, maxRetries={}, sendTimeoutSeconds={}, " +
                        "cleanupEnabled={}, retentionDays={}, dynamicBatching={}, circuitBreaker={}",
                topic, batchSize, maxRetries, sendTimeoutSeconds, cleanupEnabled, retentionDays,
                dynamicBatchingEnabled, circuitBreakerEnabled);
    }

    /**
     * Periodically publish PENDING events to Kafka.
     * <p>
     * Runs at configured poll-interval-ms intervals.
     * Supports Circuit Breaker and dynamic batch size adjustment.
     */
    @Scheduled(fixedDelayString = "${curve.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        // Check Circuit Breaker
        if (circuitBreakerEnabled && !shouldAllowRequest()) {
            log.debug("Circuit breaker is OPEN, skipping outbox processing");
            return;
        }

        try {
            // Calculate dynamic batch size
            int effectiveBatchSize = calculateEffectiveBatchSize();

            List<OutboxEvent> pendingEvents = outboxRepository.findPendingForProcessing(effectiveBatchSize);

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Processing {} pending outbox events (batchSize: {}, circuitState: {})",
                    pendingEvents.size(), effectiveBatchSize, getCircuitState());

            // Process events
            int successCount = 0;
            int failureCount = 0;

            for (OutboxEvent event : pendingEvents) {
                try {
                    processEvent(event);
                    successCount++;
                    recordSuccess();
                } catch (Exception e) {
                    failureCount++;
                    recordFailure();
                    log.warn("Failed to process outbox event: eventId={}", event.getEventId(), e);
                }
            }

            log.debug("Outbox batch completed: success={}, failure={}, total={}",
                    successCount, failureCount, pendingEvents.size());

        } catch (Exception e) {
            log.error("Failed to process pending outbox events", e);
            recordFailure();
        }
    }

    /**
     * Calculate dynamic batch size.
     * <p>
     * Adjusts batch size based on queue depth (number of pending events).
     * Deeper queues are processed with larger batches to increase throughput.
     */
    private int calculateEffectiveBatchSize() {
        if (!dynamicBatchingEnabled) {
            return batchSize;
        }

        long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

        // Dynamic batch size calculation logic
        // Example: Double batch size if pending count is 1000 or more
        if (pendingCount > 1000) {
            int dynamicSize = Math.min(batchSize * 2, 500); // Maximum 500
            log.debug("High queue depth detected ({}), increasing batch size to {}", pendingCount, dynamicSize);
            return dynamicSize;
        } else if (pendingCount > 500) {
            int dynamicSize = Math.min((int) (batchSize * 1.5), 300); // Maximum 300
            return dynamicSize;
        } else if (pendingCount < 10) {
            // Use smaller batch if queue is nearly empty
            return Math.min(batchSize, 10);
        }

        return batchSize;
    }

    /**
     * Circuit Breaker: Check whether to allow request.
     */
    private boolean shouldAllowRequest() {
        if (!circuitOpen) {
            return true; // Circuit Closed (normal)
        }

        // Attempt Half-Open (after certain time elapsed since circuit opened)
        long now = System.currentTimeMillis();
        if (now - circuitOpenedAt >= CIRCUIT_OPEN_DURATION_MS) {
            log.info("Circuit breaker transitioning to HALF-OPEN state, attempting recovery");
            return true;
        }

        return false; // Circuit Open (blocked)
    }

    /**
     * Record success (Circuit Breaker).
     */
    private void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());

        if (circuitOpen) {
            log.info("Circuit breaker transitioning to CLOSED state after successful request");
            circuitOpen = false;
            circuitOpenedAt = 0;
        }
    }

    /**
     * Record failure (Circuit Breaker).
     */
    private void recordFailure() {
        if (!circuitBreakerEnabled) {
            return;
        }

        int failures = consecutiveFailures.incrementAndGet();

        if (!circuitOpen && failures >= FAILURE_THRESHOLD) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            log.error("Circuit breaker OPENED after {} consecutive failures. " +
                            "Will retry after {}ms. This indicates Kafka may be unhealthy.",
                    failures, CIRCUIT_OPEN_DURATION_MS);
        }
    }

    /**
     * Query Circuit Breaker state.
     */
    private String getCircuitState() {
        if (!circuitBreakerEnabled) {
            return "DISABLED";
        }
        if (!circuitOpen) {
            return "CLOSED";
        }
        long now = System.currentTimeMillis();
        if (now - circuitOpenedAt >= CIRCUIT_OPEN_DURATION_MS) {
            return "HALF-OPEN";
        }
        return "OPEN";
    }

    /**
     * Clean up old PUBLISHED events.
     * <p>
     * Runs at configured cleanup-cron intervals.
     */
    @Scheduled(cron = "${curve.outbox.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupOldEvents() {
        if (!cleanupEnabled) {
            return;
        }

        log.info("Starting outbox cleanup job (retentionDays={})", retentionDays);
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = 0;
        int batchDeleteSize = 1000; // Maximum number to delete at once

        try {
            // Delete iteratively to distribute transaction load during bulk deletion
            while (true) {
                int count = outboxRepository.deleteByStatusAndOccurredAtBefore(
                        OutboxStatus.PUBLISHED, before, batchDeleteSize
                );
                deletedCount += count;
                if (count < batchDeleteSize) {
                    break;
                }
            }
            log.info("Outbox cleanup completed. Deleted {} events older than {}", deletedCount, before);
        } catch (Exception e) {
            log.error("Failed to cleanup old outbox events", e);
        }
    }

    /**
     * Process individual event.
     *
     * @param event Event to process
     */
    private void processEvent(OutboxEvent event) {
        try {
            // Publish to Kafka (with timeout)
            kafkaTemplate.send(topic, event.getEventId(), event.getPayload())
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);

            // Publish success
            event.markAsPublished();
            outboxRepository.save(event);
            publishedCount.incrementAndGet();

            log.debug("Outbox event published successfully: eventId={}, aggregateType={}, aggregateId={}",
                    event.getEventId(), event.getAggregateType(), event.getAggregateId());

        } catch (Exception e) {
            // Publish failure
            handlePublishFailure(event, e);
        }
    }

    /**
     * Handle publish failure.
     *
     * @param event Failed event
     * @param error Exception that occurred
     */
    private void handlePublishFailure(OutboxEvent event, Exception error) {
        // Apply exponential backoff (1 second, 2 seconds, 4 seconds, 8 seconds...)
        long backoffMs = (long) Math.pow(2, event.getRetryCount()) * 1000L;
        int retryCount = event.scheduleNextRetry(backoffMs);

        log.warn("Failed to publish outbox event (attempt {}/{}): eventId={}, nextRetryIn={}ms, error={}",
                retryCount, maxRetries, event.getEventId(), backoffMs, error.getMessage());

        if (event.exceededMaxRetries(maxRetries)) {
            // Maximum retries exceeded
            event.markAsFailed(truncate(error.getMessage(), 500));
            failedCount.incrementAndGet();

            log.error("Outbox event permanently failed after {} retries: eventId={}, aggregateType={}, aggregateId={}",
                    maxRetries, event.getEventId(), event.getAggregateType(), event.getAggregateId());
        }

        outboxRepository.save(event);
    }

    /**
     * Query publishing statistics.
     *
     * @return Statistics information
     */
    public PublisherStats getStats() {
        long totalPending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long totalPublished = outboxRepository.countByStatus(OutboxStatus.PUBLISHED);
        long totalFailed = outboxRepository.countByStatus(OutboxStatus.FAILED);

        return new PublisherStats(
                totalPending,
                totalPublished,
                totalFailed,
                publishedCount.get(),
                failedCount.get(),
                getCircuitState(),
                consecutiveFailures.get(),
                System.currentTimeMillis() - lastSuccessTime.get()
        );
    }

    /**
     * Reset statistics.
     */
    public void resetStats() {
        publishedCount.set(0);
        failedCount.set(0);
        consecutiveFailures.set(0);
        circuitOpen = false;
        circuitOpenedAt = 0;
        lastSuccessTime.set(System.currentTimeMillis());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * Outbox Publisher statistics.
     *
     * @param totalPending             Total PENDING events count
     * @param totalPublished           Total PUBLISHED events count
     * @param totalFailed              Total FAILED events count
     * @param publishedCountSinceStart Published events count since start
     * @param failedCountSinceStart    Failed events count since start
     * @param circuitBreakerState      Circuit Breaker state (CLOSED, OPEN, HALF-OPEN, DISABLED)
     * @param consecutiveFailures      Consecutive failures count
     * @param timeSinceLastSuccessMs   Time elapsed since last success (milliseconds)
     */
    public record PublisherStats(
            long totalPending,
            long totalPublished,
            long totalFailed,
            int publishedCountSinceStart,
            int failedCountSinceStart,
            String circuitBreakerState,
            int consecutiveFailures,
            long timeSinceLastSuccessMs
    ) {
    }
}
