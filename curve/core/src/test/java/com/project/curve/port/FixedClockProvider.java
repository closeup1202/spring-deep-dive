package com.project.curve.port;

import com.project.curve.core.port.ClockProvider;

import java.time.Instant;

public final class FixedClockProvider implements ClockProvider {

    private final Instant fixed;

    public FixedClockProvider(Instant fixed) {
        this.fixed = fixed;
    }

    @Override
    public Instant now() {
        return fixed;
    }
}