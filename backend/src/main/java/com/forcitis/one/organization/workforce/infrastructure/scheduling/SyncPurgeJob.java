package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.kernel.BusinessZone;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
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

/**
 * Data scorecard K7 (GUIDING_PRINCIPLES section 6): sync diffs go after one year, sync runs after two, counted from the release
 * when a held run was released, otherwise from the start. A run still running or held and not released is never removed.
 */
@Component
@EnableConfigurationProperties(SyncPurgeJob.KeepPeriods.class)
@ConditionalOnProperty(prefix = "forcitis.organization.workforce.sync-purge", name = "enabled", havingValue = "true", matchIfMissing = true)
class SyncPurgeJob {
    private static final Logger LOG = LoggerFactory.getLogger(SyncPurgeJob.class);

    @ConfigurationProperties("forcitis.organization.workforce.sync-purge")
    record KeepPeriods(Period runKeepPeriod, Period diffKeepPeriod) {
        KeepPeriods {
            require(runKeepPeriod, "run-keep-period");
            require(diffKeepPeriod, "diff-keep-period");
            if (diffKeepPeriod.toTotalMonths() > runKeepPeriod.toTotalMonths()) {
                throw new IllegalArgumentException("forcitis.organization.workforce.sync-purge.diff-keep-period must not be longer than run-keep-period: a diff belongs to its run");
            }
        }

        private static void require(Period period, String key) {
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("forcitis.organization.workforce.sync-purge." + key + " is required and must be positive");
            }
        }
    }

    private static final String SETTLED = "status not in ('RUNNING', 'HELD') and coalesce(released_at, started_at) < :cutoff";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;
    private final KeepPeriods keep;

    SyncPurgeJob(JdbcClient jdbc, Clock clock, BusinessZone zone, KeepPeriods keep) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
        this.keep = keep;
    }

    @Scheduled(cron = "${forcitis.organization.workforce.sync-purge.cron}")
    @SchedulerLock(name = "organization.workforce.sync.purge", lockAtMostFor = "PT30M")
    void purge() {
        int diffs = deleteDiffs();
        int runs = deleteRuns();
        LOG.info("organization.workforce removed {} sync diffs older than {} and {} sync runs older than {}", diffs, keep.diffKeepPeriod(), runs,
                keep.runKeepPeriod());
    }

    private int deleteDiffs() {
        return jdbc.sql("""
                delete from organization_workforce.sync_diffs where sync_run_id in (select id from organization_workforce.sync_runs where
                """
                + SETTLED + ")").param("cutoff", before(keep.diffKeepPeriod())).update();
    }

    private int deleteRuns() {
        return jdbc.sql("""
                delete from organization_workforce.sync_runs where
                """ + SETTLED).param("cutoff", before(keep.runKeepPeriod())).update();
    }

    private OffsetDateTime before(Period period) {
        return OffsetDateTime.ofInstant(Instant.from(clock.instant().atZone(zone.zone()).minus(period)), ZoneOffset.UTC);
    }
}
