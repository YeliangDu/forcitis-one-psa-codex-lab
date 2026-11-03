package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.BusinessZone;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpholdExpiry: the first day of the next natural quarter, 00:00 in the business zone (ADR-0044 decision 2 item 2)")
class UpholdExpiryTest {

    private static final BusinessZone SHANGHAI = new BusinessZone(ZoneId.of("Asia/Shanghai"));

    private static Instant after(String instant) {
        return UpholdExpiry.after(Instant.parse(instant), SHANGHAI).value();
    }

    @Test
    void theNextQuarterStartInTheBusinessZone() {
        assertThat(after("2026-11-02T02:00:00Z")).isEqualTo(Instant.parse("2026-12-31T16:00:00Z"));
        assertThat(after("2026-12-31T15:59:00Z")).as("23:59 local of the last day of the year").isEqualTo(Instant.parse("2026-12-31T16:00:00Z"));
    }

    @Test
    void anInstantExactlyAtAQuarterStartGetsTheQuarterAfter() {
        assertThat(after("2026-12-31T16:00:00Z")).isEqualTo(Instant.parse("2027-03-31T16:00:00Z"));
        assertThat(after("2026-09-30T16:00:00Z")).isEqualTo(Instant.parse("2026-12-31T16:00:00Z"));
    }
}
