package com.project.curve.spring.infrastructure;

import com.project.curve.core.port.ClockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UtcClockProvider Test")
class UtcClockProviderTest {

    @Test
    @DisplayName("Create UtcClockProvider")
    void createUtcClockProvider() {
        // when
        UtcClockProvider provider = new UtcClockProvider();

        // then
        assertNotNull(provider);
    }

    @Test
    @DisplayName("Implement ClockProvider interface")
    void implementsClockProvider() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // then
        assertTrue(provider instanceof ClockProvider);
    }

    @Test
    @DisplayName("now() method returns current time")
    void nowReturnsCurrentTime() {
        // given
        UtcClockProvider provider = new UtcClockProvider();
        Instant before = Instant.now();

        // when
        Instant now = provider.now();

        // then
        Instant after = Instant.now();
        assertNotNull(now);
        assertTrue(now.isAfter(before.minusSeconds(1)));
        assertTrue(now.isBefore(after.plusSeconds(1)));
    }

    @Test
    @DisplayName("Consecutive calls increase time")
    void consecutiveCallsIncreaseTime() throws InterruptedException {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant first = provider.now();
        Thread.sleep(10);
        Instant second = provider.now();

        // then
        assertTrue(second.isAfter(first) || second.equals(first));
    }

    @Test
    @DisplayName("Multiple instances return same time")
    void multipleInstancesReturnSameTime() {
        // given
        UtcClockProvider provider1 = new UtcClockProvider();
        UtcClockProvider provider2 = new UtcClockProvider();

        // when
        Instant time1 = provider1.now();
        Instant time2 = provider2.now();

        // then
        long diff = Math.abs(time1.toEpochMilli() - time2.toEpochMilli());
        assertTrue(diff < 100); // Within 100ms difference
    }

    @Test
    @DisplayName("Use UTC timezone")
    void usesUtcTimezone() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant now = provider.now();

        // then
        assertNotNull(now);
        // Since it's UTC time, it's same as epoch time
        assertTrue(now.getEpochSecond() > 0);
    }

    @Test
    @DisplayName("Returned Instant is not null")
    void returnedInstantIsNotNull() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant now = provider.now();

        // then
        assertNotNull(now);
    }

    @Test
    @DisplayName("Stable across multiple calls")
    void stableAcrossMultipleCalls() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when & then
        for (int i = 0; i < 100; i++) {
            Instant now = provider.now();
            assertNotNull(now);
            assertTrue(now.getEpochSecond() > 0);
        }
    }
}
