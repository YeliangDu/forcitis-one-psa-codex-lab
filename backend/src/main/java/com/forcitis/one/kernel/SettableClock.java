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
    private final AtomicReference<Instant> held;

    public SettableClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO), new AtomicReference<>());
    }

    private SettableClock(Clock base, AtomicReference<Duration> offset, AtomicReference<Instant> held) {
        this.base = base;
        this.offset = offset;
        this.held = held;
    }

    /** From now on {@link #instant()} answers `moment` and moves on at the pace of the wrapped clock. */
    public void set(Instant moment) {
        offset.set(Duration.between(base.instant(), moment));
    }

    /**
     * Card #1499: a time limit that is read from this clock (the hour of an administrator mode) must not be revived or lengthened by moving the clock back.
     * From now on the clock may not be moved to a moment earlier than `end` while it already reads later than that moment; moving forward is always allowed.
     */
    public void holdUntil(Instant end) {
        held.accumulateAndGet(end, (old, next) -> old == null || next.isAfter(old) ? next : old);
    }

    /** False when {@link #set} to this moment, or a reset when `moment` is where it would go, is refused by {@link #holdUntil}. */
    public boolean accepts(Instant moment) {
        Instant end = held.get();
        return end == null || !moment.isBefore(instant()) || !moment.isBefore(end);
    }

    /** Where {@link #reset()} puts the clock. */
    public Instant resetMoment() {
        return base.instant();
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
        return new SettableClock(base.withZone(zone), offset, held);
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
