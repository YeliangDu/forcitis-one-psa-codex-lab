package com.forcitis.one.platform.audit.infrastructure.archive;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Audit entries go into a monthly partition (UTC months) and no partition catches the rest, so a month without a partition fails the write. This job
 * makes sure the current month and the months ahead exist: at start-up and once a day it asks the database function to create the missing ones. A failure,
 * or a next month that is still missing afterwards, is an alert. The function is the only way the application creates a partition; the application account
 * has no CREATE right.
 */
@Component
@EnableConfigurationProperties(AuditPartitionJob.Policy.class)
@ConditionalOnProperty(prefix = "forcitis.platform.audit.partitions", name = "enabled", havingValue = "true", matchIfMissing = true)
class AuditPartitionJob implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(AuditPartitionJob.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** The database function refuses a span over 24 months and the span runs from this month through the months ahead, so 23 ahead is the most. */
    static final int MAX_MONTHS_AHEAD = 23;

    @ConfigurationProperties("forcitis.platform.audit.partitions")
    record Policy(Integer monthsAhead) {
        Policy {
            if (monthsAhead == null || monthsAhead < 1 || monthsAhead > MAX_MONTHS_AHEAD) {
                throw new IllegalArgumentException("forcitis.platform.audit.partitions.months-ahead is required and must be between 1 and " + MAX_MONTHS_AHEAD
                        + " (the database function creates at most 24 months, this month included)");
            }
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final Policy policy;
    private final Counter failures;

    AuditPartitionJob(JdbcClient jdbc, Clock clock, Policy policy, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.policy = policy;
        this.failures = Counter.builder("forcitis.platform.audit.partitions.failures")
                .description("Runs where the audit partitions could not be created or the next month's partition is missing").register(meters);
    }

    @Override
    public void afterSingletonsInstantiated() {
        ensurePartitions();
    }

    @Scheduled(cron = "${forcitis.platform.audit.partitions.cron}")
    @SchedulerLock(name = "platform.audit.entry.partitions", lockAtMostFor = "PT10M")
    void run() {
        ensurePartitions();
    }

    void ensurePartitions() {
        LocalDate thisMonth = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate through = thisMonth.plusMonths(policy.monthsAhead());
        try {
            int created = jdbc.sql("""
                    select platform_audit.create_audit_entry_partitions(:from, :through)
                    """)
                    .param("from", thisMonth).param("through", through).query(Integer.class).single();
            LOG.info("platform.audit created {} audit partitions up to {}", created, through);
            String next = "platform_audit.audit_entries_p" + thisMonth.plusMonths(1).format(MONTH);
            if (Boolean.FALSE.equals(jdbc.sql("""
                    select to_regclass(:name) is not null
                    """).param("name", next).query(Boolean.class).single())) {
                failures.increment();
                LOG.error("ALERT platform.audit the audit partition {} of next month is missing: entries of that month will be refused", next);
            }
        } catch (RuntimeException failure) {
            failures.increment();
            LOG.error("ALERT platform.audit audit partitions up to {} could not be created: {}", through, failure.getMessage(), failure);
        }
    }
}
