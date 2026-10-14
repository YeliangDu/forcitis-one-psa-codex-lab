package com.forcitis.one.organization.workforce.infrastructure.metrics;

import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Values are read from the run log on every scrape; a failing read reports NaN instead of failing the scrape. */
@Component
class SyncMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(SyncMetrics.class);

    private final SyncRunQueries runs;
    private final Clock clock;
    private final Instant startedAt;

    SyncMetrics(SyncRunQueries runs, MeterRegistry registry, ObjectProvider<Clock> clock) {
        this.runs = runs;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
        this.startedAt = this.clock.instant();
        Gauge.builder("forcitis.organization.workforce.sync.lag.seconds", this, SyncMetrics::lagSeconds)
                .description("Seconds since the last successful org snapshot sync (since start-up if there was none)")
                .register(registry);
        Gauge.builder("forcitis.organization.workforce.sync.held.runs", this, SyncMetrics::heldRuns)
                .description("Sync runs held by the shrink guard and waiting for an administrator")
                .register(registry);
        Gauge.builder("forcitis.organization.workforce.employees.without.wecom", this, SyncMetrics::employedWithoutWecom)
                .description("Employed people without a WeCom user id after the latest successful sync")
                .register(registry);
    }

    double lagSeconds() {
        try {
            Instant reference = runs.findLastSucceededAt().orElse(startedAt);
            return Math.max(0, Duration.between(reference, clock.instant()).toSeconds());
        } catch (RuntimeException unreadable) {
            LOG.warn("organization.workforce lag metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }

    double heldRuns() {
        try {
            return runs.countHeld();
        } catch (RuntimeException unreadable) {
            LOG.warn("organization.workforce held metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }

    double employedWithoutWecom() {
        try {
            return runs.findLatestEmployedWithoutWecomCount().map(Integer::doubleValue).orElse(Double.NaN);
        } catch (RuntimeException unreadable) {
            LOG.warn("organization.workforce data-quality metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }
}
