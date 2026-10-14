package com.forcitis.one.platform.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryScheduleTest {

    static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    @Test
    @DisplayName("ARCH 3.7 back-off after the 1st to 7th failure: 30 s, 2 min, 10 min, 30 min, 2 h, 2 h, 2 h")
    void backOffGrows() {
        assertThat(delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(delayAfter(3)).isEqualTo(Duration.ofMinutes(10));
        assertThat(delayAfter(4)).isEqualTo(Duration.ofMinutes(30));
        assertThat(delayAfter(5)).isEqualTo(Duration.ofHours(2));
        assertThat(delayAfter(6)).isEqualTo(Duration.ofHours(2));
        assertThat(delayAfter(7)).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("ARCH 3.7 the 8th failure ends in the dead letters")
    void eighthFailureIsFinal() {
        assertThat(RetrySchedule.MAX_ATTEMPTS).isEqualTo(8);
        assertThat(RetrySchedule.afterFailure(8, NOW)).isEmpty();
        assertThat(RetrySchedule.afterFailure(9, NOW)).isEmpty();
    }

    private static Duration delayAfter(int attemptsMade) {
        return Duration.between(NOW, RetrySchedule.afterFailure(attemptsMade, NOW).orElseThrow());
    }
}
