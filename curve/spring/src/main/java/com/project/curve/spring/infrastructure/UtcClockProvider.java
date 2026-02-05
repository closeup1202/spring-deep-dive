package com.project.curve.spring.infrastructure;

import com.project.curve.core.port.ClockProvider;

import java.time.Clock;
import java.time.Instant;

public class UtcClockProvider implements ClockProvider {

    private final Clock clock;

    public UtcClockProvider() {
        this.clock = Clock.systemUTC();
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
