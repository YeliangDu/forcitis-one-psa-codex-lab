package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Data scorecard T3: handled-event records outlive the redelivery window (event archive: 30 days) by a margin, then go. */
@Component
@EnableConfigurationProperties(ConsumedEventsPurgeJob.KeepPeriod.class)
@ConditionalOnProperty(prefix = "forcitis.cost.timesheet.consumed-events", name = "enabled", havingValue = "true", matchIfMissing = true)
class ConsumedEventsPurgeJob {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumedEventsPurgeJob.class);

    @ConfigurationProperties("forcitis.cost.timesheet.consumed-events")
    record KeepPeriod(Duration keepPeriod) {
        KeepPeriod {
            if (keepPeriod == null || keepPeriod.isZero() || keepPeriod.isNegative()) {
                throw new IllegalArgumentException("forcitis.cost.timesheet.consumed-events.keep-period is required and must be positive");
            }
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration keepPeriod;

    ConsumedEventsPurgeJob(JdbcClient jdbc, Clock clock, KeepPeriod keepPeriod) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.keepPeriod = keepPeriod.keepPeriod();
    }

    @Scheduled(cron = "${forcitis.cost.timesheet.consumed-events.cron}")
    @SchedulerLock(name = "cost.timesheet.consumed.events.purge", lockAtMostFor = "PT30M")
    void purge() {
        LOG.info("cost.timesheet removed {} handled-event records older than {}", deleteOlderThan(clock.instant().minus(keepPeriod)), keepPeriod);
    }

    private int deleteOlderThan(Instant cutoff) {
        return jdbc.sql("""
                delete from cost_timesheet.consumed_events where consumed_at < :cutoff
                """)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)).update();
    }
}
