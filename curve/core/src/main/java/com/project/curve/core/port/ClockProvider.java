package com.project.curve.core.port;

import java.time.Instant;

public interface ClockProvider {
    Instant now();
}