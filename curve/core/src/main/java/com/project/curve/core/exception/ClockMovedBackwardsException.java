package com.project.curve.core.exception;

import lombok.Getter;

/**
 * Exception thrown when system time moves backwards.
 * When generating Snowflake IDs, time-based ID uniqueness must be guaranteed.
 * If time moves backwards, there's a possibility of ID collision, so an exception is thrown.
 */
@Getter
public class ClockMovedBackwardsException extends RuntimeException {

    private final long lastTimestamp;
    private final long currentTimestamp;

    public ClockMovedBackwardsException(long lastTimestamp, long currentTimestamp) {
        super(String.format(
                "Clock moved backwards. Refusing to generate ID. lastTimestamp=%d, currentTimestamp=%d, diff=%dms",
                lastTimestamp, currentTimestamp, lastTimestamp - currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
    }

    public ClockMovedBackwardsException(String message) {
        super(message);
        this.lastTimestamp = -1;
        this.currentTimestamp = -1;
    }

    public ClockMovedBackwardsException(String message, Throwable cause) {
        super(message, cause);
        this.lastTimestamp = -1;
        this.currentTimestamp = -1;
    }

    public long getDifferenceMs() {
        return lastTimestamp - currentTimestamp;
    }
}
