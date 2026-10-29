package com.forcitis.one.fixture.legacy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Allowed: LocalDateTime as a local step of a calculation, never in a field or signature. Only LegacyApiGuardTest loads it. */
public final class LocalDateTimeInsideSample {
    public Instant startOfYear(int year) {
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
        return start.toInstant(ZoneOffset.UTC);
    }
}
