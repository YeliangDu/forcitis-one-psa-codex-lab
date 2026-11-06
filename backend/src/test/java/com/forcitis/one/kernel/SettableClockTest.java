package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Card #1478: the settable clock answers the set moment at the pace of the clock it wraps, and can go back. */
class SettableClockTest {

    @Test
    @DisplayName("#1478 set puts the clock on a golden date and it moves on with the wrapped clock; reset puts it back; a zoned copy shares the setting")
    void setsAndResets() {
        Instant real = Instant.parse("2026-11-05T10:00:00Z");
        Instant golden = Instant.parse("2026-03-15T08:00:00Z");
        var wrapped = new Clock() {
            Instant now = real;

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
                return now;
            }
        };
        SettableClock clock = new SettableClock(wrapped);

        assertThat(clock.isSet()).isFalse();
        assertThat(clock.instant()).isEqualTo(real);

        clock.set(golden);
        wrapped.now = real.plusSeconds(90);

        assertThat(clock.isSet()).isTrue();
        assertThat(clock.instant()).isEqualTo(golden.plus(Duration.ofSeconds(90)));
        assertThat(clock.withZone(ZoneId.of("Asia/Shanghai")).instant()).isEqualTo(golden.plusSeconds(90));

        clock.reset();
        assertThat(clock.isSet()).isFalse();
        assertThat(clock.instant()).isEqualTo(wrapped.now);
    }
}
