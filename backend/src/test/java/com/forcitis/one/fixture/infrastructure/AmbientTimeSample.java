package com.forcitis.one.fixture.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/** Deliberately wrong: reads the time of the machine instead of an injected Clock. Only TimeSourceGuardTest loads it. */
public final class AmbientTimeSample {
    public Object[] everyWayToAskTheMachine() {
        return new Object[] {ZonedDateTime.now(), OffsetDateTime.now(), LocalDateTime.now(), LocalDate.now(), Instant.now(),
                Clock.systemUTC(), Clock.systemDefaultZone(), System.currentTimeMillis()};
    }

    public java.util.function.Supplier<Clock> byReference() {
        return Clock::systemUTC;
    }
}
