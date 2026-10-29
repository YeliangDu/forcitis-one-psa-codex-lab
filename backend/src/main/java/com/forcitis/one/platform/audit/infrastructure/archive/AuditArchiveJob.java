package com.forcitis.one.platform.audit.infrastructure.archive;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * Audit entries older than the online period (3 years) are written to the archive store in batches, in (created_at, id) order and only as a prefix of
 * the entries that all qualify, so a late-written old entry is never skipped; the position is a moment and an id, never an id alone. A batch counts as archived when the vault confirmed the
 * object and the batch row is saved (the progress). An old entry written after the archive passed its moment is never archived and shows only as a
 * reconciliation alert, so a migration of old entries must be loaded before the archive position moves past their dates. Online rows are never deleted row by row (the table refuses DELETE, G34); a whole month leaves only when the maintenance role removes its partition after the batches
 * of that month are checked against it (platform_audit.drop_archived_audit_entry_partition). A batch never spans two UTC months. The archive is kept by the bucket's WORM retention; the application never deletes it.
 */
@Component
@EnableConfigurationProperties(AuditArchiveJob.Policy.class)
@ConditionalOnProperty(prefix = "forcitis.platform.audit.archive", name = "enabled", havingValue = "true", matchIfMissing = true)
class AuditArchiveJob implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(AuditArchiveJob.class);
    static final String KIND = "audit";
    static final int BATCH = 5000;
    /** The columns the archive carries; architecture/archive-columns.txt names every column of the table as archived or excluded and AuditArchiveColumnsTest keeps both equal. */
    static final List<String> COLUMNS = List.of("id", "action", "actor_id", "on_behalf_of_id", "subject_kind", "subject_id", "detail", "event_id", "correlation_id", "source_address", "outcome_kind", "login_method_kind", "change_reason", "before_text", "after_text", "created_at");

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
    private final IdGenerator ids;
    private final BusinessZone zone;
    private final Policy policy;
    private final Counter probeFailures;
    private final Counter reconciliationFailures;
    private final JsonMapper json = JsonMapper.builder().build();

    AuditArchiveJob(JdbcClient jdbc, ArchiveVault vault, Clock clock, IdGenerator ids, BusinessZone zone, Policy policy, MeterRegistry meters) {
        this.ids = ids;
        this.jdbc = jdbc;
        this.vault = vault;
        this.clock = clock;
        this.zone = zone;
        this.policy = policy;
        vault.requireTotalKeepPeriod("audit archive", policy.onlinePeriod());
        this.probeFailures = Counter.builder("forcitis.platform.audit.archive.probe.failures")
                .description("Failed write probes of the audit archive target").register(meters);
        this.reconciliationFailures = Counter.builder("forcitis.platform.audit.archive.reconciliation.failures")
                .description("Runs where the entries up to the archived position differ in number from the archived batches").register(meters);
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
        reconcile();
    }

    /**
     * Archiving goes by (created_at, id) and remembers the last entry's moment and id; an entry written later with an earlier moment
     * (a clock step) would never be archived. Every online entry up to the archived position must be in a batch of an online month (a month whose
     * partition the maintenance role has removed is no longer compared), and none is deleted row by row, so the counts must match.
     */
    void reconcile() {
        Optional<Position> last = position();
        if (last.isEmpty()) {
            return;
        }
        List<String> online = jdbc.sql("""
                select substring(c.relname from '[0-9]{6}$') from pg_inherits i join pg_class c on c.oid = i.inhrelid
                 where i.inhparent = 'platform_audit.audit_entries'::regclass
                """).query(String.class).list();
        if (online.isEmpty()) {
            return;
        }
        long archived = jdbc.sql("""
                select coalesce(sum(entry_count), 0) from platform_audit.audit_archive_batches where to_char(first_entry_created_at at time zone 'UTC', 'YYYYMM') in (:online)
                """)
                .param("online", online).query(Long.class).single();
        long upToTheLast = jdbc.sql("""
                select count(*) from platform_audit.audit_entries where (created_at, id) <= (:at, :id)
                """)
                .param("at", last.get().createdAt()).param("id", last.get().entryId()).query(Long.class).single();
        if (archived != upToTheLast) {
            reconciliationFailures.increment();
            LOG.error("ALERT platform.audit the audit trail up to entry {} has {} entries, the archived batches hold {}: entries are missing from the archive",
                    last.get().entryId(), upToTheLast, archived);
        }
    }

    private record Position(Timestamp createdAt, UUID entryId) {
    }

    private Optional<Position> position() {
        return jdbc.sql("""
                select last_entry_created_at, last_entry_id from platform_audit.audit_archive_batches order by last_entry_created_at desc, last_entry_id desc limit 1
                """)
                .query((rs, n) -> new Position(rs.getTimestamp("last_entry_created_at"), rs.getObject("last_entry_id", UUID.class))).optional();
    }

    int archiveOlderThan(Instant cutoff) {
        int total = 0;
        while (true) {
            Optional<Position> last = position();
            String after = last.isPresent() ? " where (created_at, id) > (:at, :id)" : "";
            var select = jdbc.sql("""
                    select
                    """ + String.join(", ", COLUMNS) + """
                             from platform_audit.audit_entries
                            """ + after + """
                                     order by created_at, id limit :batch
                                    """);
            if (last.isPresent()) {
                select = select.param("at", last.get().createdAt()).param("id", last.get().entryId());
            }
            List<Map<String, Object>> entries = select.param("batch", BATCH).query((rs, n) -> row(rs)).list();
            int due = 0;
            while (due < entries.size() && Instant.parse((String) entries.get(due).get("created_at")).isBefore(cutoff)) {
                due++;
            }
            if (due == 0) {
                return total;
            }
            YearMonth month = monthOf(entries.getFirst());
            int inMonth = 1;
            while (inMonth < due && monthOf(entries.get(inMonth)).equals(month)) {
                inMonth++;
            }
            List<Map<String, Object>> batch = entries.subList(0, inMonth);
            ArchivedObject stored = vault.archive(KIND, batch.stream().map(json::writeValueAsString).toList());
            insertBatch(batch.getFirst(), batch.getLast(), batch.size(), stored);
            total += batch.size();
            if (inMonth == due && due < BATCH) {
                return total;
            }
        }
    }

    /** A batch never spans two UTC months, so every batch belongs to one partition and a partition can be checked against its batches. */
    private static YearMonth monthOf(Map<String, Object> entry) {
        return YearMonth.from(Instant.parse((String) entry.get("created_at")).atOffset(ZoneOffset.UTC));
    }

    /** Only the listed columns, only types whose JSON form is unambiguous; any other type is a failure, not a toString. */
    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        ResultSetMetaData columns = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columns.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            row.put(columns.getColumnName(i), switch (value) {
                case null -> null;
                case Long number -> number;
                case Integer number -> number;
                case Short number -> number;
                case Boolean flag -> flag;
                case String text -> text;
                case UUID id -> id.toString();
                case Timestamp time -> time.toInstant().toString();
                case OffsetDateTime time -> time.toInstant().toString();
                default -> throw new IllegalStateException("audit_entries." + columns.getColumnName(i) + " is a " + value.getClass().getName()
                        + ": the archive does not know how to write that type; extend the job deliberately");
            });
        }
        return row;
    }

    private void insertBatch(Map<String, Object> first, Map<String, Object> last, int count, ArchivedObject stored) {
        jdbc.sql("""
                insert into platform_audit.audit_archive_batches (id, first_entry_id, first_entry_created_at, last_entry_id, last_entry_created_at,
                                                                  entry_count, object_key, sha256_hash, archived_at)
                values (:id, :first, :firstAt, :last, :lastAt, :count, :key, :sha, :now)
                """).param("id", ids.next()).param("first", UUID.fromString((String) first.get("id")))
                .param("firstAt", Timestamp.from(Instant.parse((String) first.get("created_at"))))
                .param("last", UUID.fromString((String) last.get("id"))).param("lastAt", Timestamp.from(Instant.parse((String) last.get("created_at"))))
                .param("count", count)
                .param("key", stored.key()).param("sha", stored.sha256()).param("now", Timestamp.from(clock.instant())).update();
    }
}
