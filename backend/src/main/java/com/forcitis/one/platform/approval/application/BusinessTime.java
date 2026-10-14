package com.forcitis.one.platform.approval.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Business dates are cut in the legal entities' time zone (Asia/Shanghai), never in the server's. */
@Component
class BusinessTime {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectProvider<Clock> clock;

    BusinessTime(ObjectProvider<Clock> clock) {
        this.clock = clock;
    }

    Instant now() {
        return clock.getIfAvailable(Clock::systemUTC).instant();
    }

    LocalDate today() {
        return LocalDate.ofInstant(now(), ZONE);
    }
}
