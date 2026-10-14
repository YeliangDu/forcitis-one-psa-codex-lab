package com.forcitis.one.fixture.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Deliberately wrong: a class in a domain package that reads the time. Only EventBusinessDateGuardTest loads it. */
public final class ClockReadingSample {
    final Clock clock = Clock.systemUTC();

    public LocalDate today() {
        return LocalDate.now();
    }

    public LocalDate todayIn(ZoneId zone) {
        return LocalDate.now(zone);
    }

    public Instant instant() {
        return Instant.now();
    }

    public OffsetDateTime offset() {
        return OffsetDateTime.now();
    }
}
