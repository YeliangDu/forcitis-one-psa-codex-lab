package com.forcitis.one.platform.identity;

import com.forcitis.one.BusinessDay;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {

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
        return BusinessDay.instant().plus(offset);
    }
}
