package com.forcitis.one.platform.audit.infrastructure.archive;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.platform.integration.api.ArchiveVault;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Audit entries older than the online period (3 years) are written to the archive store in batches, in id order and only as a prefix of
 * the ids that all qualify, so a late-written old entry is never skipped. A batch counts as archived when the vault confirmed the
 * object and the batch row is saved (the progress). Online rows are kept: the table refuses DELETE (G34), and removing them needs
 * its own decision. The archive is kept by the bucket's WORM retention; the application never deletes it.
 */
@Component
@EnableConfigurationProperties(AuditArchiveJob.Policy.class)
@ConditionalOnProperty(prefix = "forcitis.platform.audit.archive", name = "enabled", havingValue = "true", matchIfMissing = true)
class AuditArchiveJob implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(AuditArchiveJob.class);
    static final String KIND = "audit";
    static final int BATCH = 5000;

    @ConfigurationProperties("forcitis.platform.audit.archive")
    record Policy(Period onlinePeriod) {
        Policy {
            if (onlinePeriod == null || onlinePeriod.isZero() || onlinePeriod.isNegative()) {
                throw new IllegalArgumentException("forcitis.platform.audit.archive.online-period is required and must be positive");
            }
        }
    }

    private final JdbcClient jdbc;
    private final ArchiveVault vault;
    private final Clock clock;
    private final BusinessZone zone;
    private final Policy policy;
    private final Counter probeFailures;
    private final JsonMapper json = JsonMapper.builder().build();

    AuditArchiveJob(JdbcClient jdbc, ArchiveVault vault, Clock clock, BusinessZone zone, Policy policy, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.vault = vault;
        this.clock = clock;
        this.zone = zone;
        this.policy = policy;
        vault.requireTotalKeepPeriod("audit archive", policy.onlinePeriod());
        this.probeFailures = Counter.builder("forcitis.platform.audit.archive.probe.failures")
                .description("Failed write probes of the audit archive target").register(meters);
    }

    @Override
    public void afterSingletonsInstantiated() {
        probe();
    }

    @Scheduled(cron = "${forcitis.platform.audit.archive.probe-cron}")
    @SchedulerLock(name = "platform.audit.entry.archive.probe", lockAtMostFor = "PT10M")
    void probeDaily() {
        probe();
    }

    void probe() {
        try {
            vault.probe(KIND);
        } catch (RuntimeException failure) {
            LOG.error("ALERT platform.audit the audit archive target is not usable: {}", failure.getMessage(), failure);
            probeFailures.increment();
        }
    }

    @Scheduled(cron = "${forcitis.platform.audit.archive.cron}")
    @SchedulerLock(name = "platform.audit.entry.archive", lockAtMostFor = "PT2H")
    void run() {
        int archived = archiveOlderThan(Instant.from(clock.instant().atZone(zone.zone()).minus(policy.onlinePeriod())));
        LOG.info("platform.audit archived {} audit entries", archived);
    }

    int archiveOlderThan(Instant cutoff) {
        int total = 0;
        while (true) {
            long last = jdbc.sql("select coalesce(max(last_entry_id), 0) from platform_audit.audit_archive_batches").query(Long.class).single();
            List<Map<String, Object>> entries = jdbc.sql("select * from platform_audit.audit_entries where id > :last order by id limit :batch")
                    .param("last", last).param("batch", BATCH).query((rs, n) -> row(rs)).list();
            int due = 0;
            while (due < entries.size() && Instant.parse((String) entries.get(due).get("created_at")).isBefore(cutoff)) {
                due++;
            }
            if (due == 0) {
                return total;
            }
            List<Map<String, Object>> batch = entries.subList(0, due);
            ArchivedObject stored = vault.archive(KIND, batch.stream().map(json::writeValueAsString).toList());
            insertBatch((Long) batch.getFirst().get("id"), (Long) batch.getLast().get("id"), batch.size(), stored);
            total += batch.size();
            if (due < BATCH) {
                return total;
            }
        }
    }

    /** Every column of the table, whatever columns it has by the time this runs: a column added later is archived without touching this class. */
    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        ResultSetMetaData columns = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            row.put(columns.getColumnName(i), switch (value) {
                case null -> null;
                case Number number -> number;
                case Boolean flag -> flag;
                case Timestamp time -> time.toInstant().toString();
                case OffsetDateTime time -> time.toInstant().toString();
                default -> value.toString();
            });
        }
        return row;
    }

    private void insertBatch(long first, long last, int count, ArchivedObject stored) {
        jdbc.sql("""
                insert into platform_audit.audit_archive_batches (first_entry_id, last_entry_id, entry_count, object_key, sha256_hash, archived_at)
                values (:first, :last, :count, :key, :sha, :now)
                """).param("first", first).param("last", last).param("count", count)
                .param("key", stored.key()).param("sha", stored.sha256()).param("now", Timestamp.from(clock.instant())).update();
    }
}
