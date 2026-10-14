package com.forcitis.one.platform.approval.infrastructure.flowable;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.flowable.engine.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Flowable moves an async or timer job to the dead letter table once its retries are used up; nothing runs it again until an operator does. */
@Component
class DeadLetterJobMetrics {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterJobMetrics.class);
    static final Duration WARN_EVERY = Duration.ofMinutes(5);

    private final ManagementService management;
    private final Clock clock;
    private Instant lastWarned;

    DeadLetterJobMetrics(ManagementService management, MeterRegistry registry, Clock clock) {
        this.management = management;
        this.clock = clock;
        Gauge.builder("forcitis.platform.approval.dead.letter.jobs", this, DeadLetterJobMetrics::deadLetterJobs)
                .description("Process engine jobs that used up their retries and wait for an operator")
                .register(registry);
    }

    double deadLetterJobs() {
        try {
            return management.createDeadLetterJobQuery().count();
        } catch (RuntimeException unreadable) {
            warnOnce(unreadable);
            return Double.NaN;
        }
    }

    /** Every scrape would repeat the same line, so it is written at most once per WARN_EVERY. */
    private synchronized void warnOnce(RuntimeException unreadable) {
        Instant now = clock.instant();
        if (lastWarned == null || Duration.between(lastWarned, now).compareTo(WARN_EVERY) >= 0) {
            lastWarned = now;
            log.warn("platform.approval dead-letter job metric unreadable: {}", unreadable.toString());
        }
    }
}
