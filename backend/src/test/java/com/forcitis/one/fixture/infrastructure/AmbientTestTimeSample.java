package com.forcitis.one.fixture.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Deliberately wrong in some methods and right in others: what a test may and may not ask of the clock. Only TimeSourceGuardTest loads it. */
public final class AmbientTestTimeSample {
    public Object[] wrong() {
        return new Object[] {LocalDate.now(), LocalDate.now(ZoneId.of("Asia/Shanghai")), YearMonth.now(ZoneOffset.UTC), Clock.systemUTC(), System.currentTimeMillis()};
    }

    public Object[] wrongInALambda() {
        java.util.function.Supplier<Instant> now = () -> Instant.now();
        java.util.function.Supplier<Clock> reference = Clock::systemDefaultZone;
        return new Object[] {now.get(), reference.get()};
    }

    public Object[] right(Clock clock) {
        return new Object[] {Instant.now(clock), LocalDate.now(clock), YearMonth.now(clock), System.nanoTime()};
    }
}
