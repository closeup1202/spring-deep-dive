package com.project.curve.spring.infrastructure;

import com.project.curve.core.envelope.EventId;
import com.project.curve.core.exception.ClockMovedBackwardsException;
import com.project.curve.core.port.IdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SnowflakeIdGenerator implements IdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 1023
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS); // 4095

    /**
     * Maximum wait time when clock moved backwards (milliseconds)
     */
    private static final long MAX_BACKWARD_MS = 100L;

    /**
     * Timeout for waitUntilNextMillis method (milliseconds)
     * Safety mechanism to prevent infinite waiting
     * Set to 5 seconds considering NTP synchronization environments
     */
    private static final long WAIT_TIMEOUT_MS = 5000L;

    /**
     * Initial wait time for exponential backoff (milliseconds)
     */
    private static final long INITIAL_BACKOFF_MS = 1L;

    /**
     * Maximum wait time for exponential backoff (milliseconds)
     */
    private static final long MAX_BACKOFF_MS = 100L;

    private final long workerId;
    private final Lock lock = new ReentrantLock();
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Constructor that explicitly specifies Worker ID
     *
     * @param workerId Unique Worker ID between 0 and 1023
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d, but got %d", MAX_WORKER_ID, workerId));
        }
        this.workerId = workerId;
        log.debug("SnowflakeIdGenerator initialized with workerId: {}", workerId);
    }

    /**
     * Constructor that auto-generates Worker ID based on MAC address
     * Warning: Collision possibility exists depending on network environment
     */
    public static SnowflakeIdGenerator createWithAutoWorkerId() {
        long generatedWorkerId = generateWorkerIdFromMacAddress();
        log.warn("Worker ID auto-generated from MAC address: {} (collision possible in distributed environment)",
                generatedWorkerId);
        return new SnowflakeIdGenerator(generatedWorkerId);
    }

    /**
     * Generates Worker ID based on MAC address
     * Uses lower 10 bits of MAC address (0 to 1023)
     */
    private static long generateWorkerIdFromMacAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();

                if (mac != null && mac.length >= 6) {
                    // Generate Worker ID using last 2 bytes of MAC address
                    long workerId = ((0x000000FF & (long) mac[mac.length - 2]) << 2)
                            | ((0x000000FF & (long) mac[mac.length - 1]) >> 6);
                    return workerId & MAX_WORKER_ID;
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to get MAC address, using default worker ID: 1", e);
        }
        // Return default value 1 if MAC address cannot be obtained
        return 1L;
    }

    @Override
    public EventId generate() {
        lock.lock();
        try {
            long timestamp = currentTimeMillis();

            // Handle clock moved backwards case
            if (timestamp < lastTimestamp) {
                long backwardMs = lastTimestamp - timestamp;

                // For small clock moves backwards (100ms or less), wait and retry
                if (backwardMs <= MAX_BACKWARD_MS) {
                    timestamp = waitUntilNextMillis(lastTimestamp);
                } else {
                    // For large clock moves backwards, throw exception
                    throw new ClockMovedBackwardsException(lastTimestamp, timestamp);
                }
            }

            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = waitUntilNextMillis(timestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            long id = ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                    | (workerId << SEQUENCE_BITS)
                    | sequence;

            return EventId.of(String.valueOf(id));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wait until next millisecond (with exponential backoff).
     * <p>
     * Uses exponential backoff considering repeated time adjustments in NTP synchronization environments.
     * Reduces CPU spinning and minimizes system load.
     *
     * @param lastTimestamp Previous timestamp
     * @return New timestamp
     * @throws ClockMovedBackwardsException When timeout or interrupt occurs
     */
    private long waitUntilNextMillis(long lastTimestamp) {
        long startTime = System.currentTimeMillis();
        long timestamp = currentTimeMillis();
        long backoffMs = INITIAL_BACKOFF_MS;
        int attempts = 0;

        while (timestamp <= lastTimestamp) {
            // Check timeout
            long elapsedMs = System.currentTimeMillis() - startTime;
            if (elapsedMs > WAIT_TIMEOUT_MS) {
                long backwardMs = lastTimestamp - timestamp;
                log.error("Clock skew timeout: waited {}ms, clock still backward by {}ms. " +
                                "This may indicate NTP sync issues or system clock problems. " +
                                "lastTimestamp={}, currentTimestamp={}, attempts={}",
                        elapsedMs, backwardMs, lastTimestamp, timestamp, attempts);
                throw new ClockMovedBackwardsException(
                        String.format("Timeout waiting for clock to advance. " +
                                        "Waited for %dms but clock is still at or before %d. Current time: %d",
                                WAIT_TIMEOUT_MS, lastTimestamp, timestamp));
            }

            // Exponential backoff wait (1ms -> 2ms -> 4ms -> ... -> 100ms)
            try {
                Thread.sleep(backoffMs);
                attempts++;

                // Double the backoff time (up to MAX_BACKOFF_MS)
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for clock to advance after {} attempts", attempts);
                throw new ClockMovedBackwardsException("Interrupted while waiting for clock to advance", e);
            }

            timestamp = currentTimeMillis();
        }

        if (attempts > 0) {
            long waitedMs = System.currentTimeMillis() - startTime;
            log.warn("Clock skew resolved: waited {}ms over {} attempts. " +
                            "Consider monitoring NTP sync if this occurs frequently. " +
                            "lastTimestamp={}, newTimestamp={}",
                    waitedMs, attempts, lastTimestamp, timestamp);
        }

        return timestamp;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
