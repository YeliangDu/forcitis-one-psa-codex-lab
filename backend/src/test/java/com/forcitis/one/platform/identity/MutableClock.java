package com.forcitis.one.platform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Real time plus an offset the test can move forward, so 60 s caches, 15 minute locks and 24 h caps need no sleeping. */
final class MutableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    void advance(Duration by) {
        offset = offset.plus(by);
    }

    void reset() {
        offset = Duration.ZERO;
    }

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
        return Instant.now().plus(offset);
    }
}
