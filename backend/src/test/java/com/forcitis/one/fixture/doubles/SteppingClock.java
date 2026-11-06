package com.forcitis.one.fixture.doubles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/** A clock that moves one millisecond each time it is read, from a fixed start: time-ordered ids made from it never repeat and never depend on the machine. */
public final class SteppingClock extends Clock {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final AtomicLong reads = new AtomicLong();

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return START.plusMillis(reads.getAndIncrement());
    }
}
