package com.forcitis.one.kernel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** The zone in which business days are cut (forcitis.kernel.business-zone, Asia/Shanghai). Instants are stored in UTC. */
public record BusinessZone(ZoneId zone) {

    public BusinessZone {
        Objects.requireNonNull(zone, "zone");
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    public LocalDate today(Clock clock) {
        return dateOf(clock.instant());
    }
}
