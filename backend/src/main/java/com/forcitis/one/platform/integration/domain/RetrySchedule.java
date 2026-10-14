package com.forcitis.one.platform.integration.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class RetrySchedule {

    public static final int MAX_ATTEMPTS = 8;
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30), Duration.ofHours(2));

    private RetrySchedule() {
    }

    /** Empty when the attempts are used up: the delivery goes to the dead letters. */
    public static Optional<Instant> afterFailure(int attemptsMade, Instant now) {
        if (attemptsMade >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        return Optional.of(now.plus(BACKOFF.get(Math.min(Math.max(attemptsMade, 1), BACKOFF.size()) - 1)));
    }
}
