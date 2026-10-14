package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.platform.approval.domain.Moment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Business dates are cut in the business zone, never in the server's. */
@Component
class BusinessTime {

    private final Clock clock;
    private final BusinessZone zone;

    BusinessTime(Clock clock, BusinessZone zone) {
        this.clock = clock;
        this.zone = zone;
    }

    Instant now() {
        return clock.instant();
    }

    LocalDate today() {
        return zone.today(clock);
    }

    Moment moment() {
        Instant now = clock.instant();
        return new Moment(now, zone.dateOf(now));
    }

    BusinessZone zone() {
        return zone;
    }

    Clock clock() {
        return clock;
    }
}
