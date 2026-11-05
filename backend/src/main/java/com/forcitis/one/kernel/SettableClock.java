package com.forcitis.one.kernel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Card #1478: the clock of a developer's own machine (built only under the profile local, see KernelConfiguration). It runs at the pace of the clock it wraps
 * but starts from a moment that can be set, so a golden date (2026-01..07) can be replayed as "today". Every reader of Clock sees it; a database default
 * such as now() does not.
 */
public final class SettableClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public SettableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private SettableClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    /** From now on {@link #instant()} answers `moment` and moves on at the pace of the wrapped clock. */
    public void set(Instant moment) {
        offset.set(Duration.between(base.instant(), moment));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public boolean isSet() {
        return !offset.get().isZero();
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new SettableClock(base.withZone(zone), offset);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
