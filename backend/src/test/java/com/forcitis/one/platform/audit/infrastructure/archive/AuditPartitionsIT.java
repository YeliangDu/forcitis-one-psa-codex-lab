package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
class AuditPartitionsIT {
    private static final String ENTRIES = "platform_audit.audit_entries";

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(AuditPartitionsIT.class, TestPostgres.MIGRATED);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    JdbcClient jdbc;

    @Autowired
    javax.sql.DataSource dataSource;

    @Autowired
    AuditEntryRepository repository;

    @Autowired
    AuditPartitionJob job;

    @AfterEach
    void clean() {
        append(false);
        jdbc.sql("delete from platform_audit.audit_archive_batches").update();
        jdbc.sql("delete from platform_audit.audit_entries").update();
        append(true);
    }

    private List<String> partitions() {
        return jdbc.sql("select c.relname from pg_inherits i join pg_class c on c.oid = i.inhrelid where i.inhparent = 'platform_audit.audit_entries'::regclass order by 1")
                .query(String.class).list();
    }

    /** The append-only triggers are not cloned for a statement trigger, so they are switched on or off on the table and on every partition. */
    private void append(boolean on) {
        String action = on ? "enable" : "disable";
        for (String table : List.of("audit_archive_batches", "audit_entries")) {
            jdbc.sql("alter table platform_audit." + table + " " + action + " trigger user").update();
        }
        partitions().forEach(partition -> jdbc.sql("alter table platform_audit." + partition + " " + action + " trigger user").update());
    }

    private UUID insertEntry(String at) {
        return jdbc.sql("""
                insert into platform_audit.audit_entries (id, created_at, updated_at, action, subject_kind, subject_id)
                values (uuidv7(), cast(:at as timestamptz), cast(:at as timestamptz), 'test.audit.partition', 'USER_ACCOUNT', gen_random_uuid()) returning id
                """).param("at", at).query(UUID.class).single();
    }

    private void insertBatch(UUID first, String firstAt, UUID last, String lastAt, int count) {
        jdbc.sql("""
                insert into platform_audit.audit_archive_batches (id, first_entry_id, first_entry_created_at, last_entry_id, last_entry_created_at, entry_count, object_key, sha256_hash, archived_at)
                values (uuidv7(), :first, cast(:firstAt as timestamptz), :last, cast(:lastAt as timestamptz), :count, 'o/' || gen_random_uuid(), repeat('a', 64), cast(:lastAt as timestamptz))
                """).param("first", first).param("firstAt", firstAt).param("last", last).param("lastAt", lastAt).param("count", count).update();
    }

    private static YearMonth ahead(int months) {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(months);
    }

    private static String day(int monthsAhead, int dayOfMonth) {
        return ahead(monthsAhead).atDay(dayOfMonth).toString();
    }

    private static String at(int monthsAhead, int dayOfMonth) {
        return day(monthsAhead, dayOfMonth) + "T00:00:00Z";
    }

    private static String partition(int monthsAhead) {
        return "audit_entries_p" + ahead(monthsAhead).toString().replace("-", "");
    }

    private long created(String from, String through) {
        return jdbc.sql("select platform_audit.create_audit_entry_partitions(cast(:from as date), cast(:through as date))").param("from", from).param("through", through)
                .query(Long.class).single();
    }

    private boolean exists(String partition) {
        return jdbc.sql("select to_regclass(:name) is not null").param("name", "platform_audit." + partition).query(Boolean.class).single();
    }

    private AuditEntry entry() {
        return new AuditEntry("test.audit.partition", null, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, UUID.randomUUID()), "detail");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 delivering the same event twice leaves one row; the entry keeps the moment of the event")
    void theSameEventIsAuditedOnce() {
        UUID event = UUID.randomUUID();
        Instant occurred = Instant.parse("2026-11-12T08:30:00Z");

        assertThat(repository.saveForEvent(entry(), occurred, event, UUID.randomUUID())).isTrue();
        assertThat(repository.saveForEvent(entry(), occurred, event, UUID.randomUUID())).isFalse();

        assertThat(jdbc.sql("select count(*) from " + ENTRIES + " where event_id = :e").param("e", event).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select created_at from " + ENTRIES + " where event_id = :e").param("e", event).query(java.sql.Timestamp.class).single().toInstant()).isEqualTo(occurred);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the UTC month decides the partition: the last microsecond of October and the first instant of November go to different partitions")
    void theMonthBoundaryIsUtc() {
        insertEntry("2026-10-31T23:59:59.999999Z");
        insertEntry("2026-11-01T00:00:00Z");
        insertEntry("2026-11-01T00:30:00+01:00");

        assertThat(jdbc.sql("select tableoid::regclass::text from " + ENTRIES + " order by created_at, id").query(String.class).list())
                .containsExactly("platform_audit.audit_entries_p202610", "platform_audit.audit_entries_p202610", "platform_audit.audit_entries_p202611");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 a moment in a month without a partition is refused, by the table and by the repository (a late event fails, it is not swallowed)")
    void aMonthWithoutPartitionIsRefused() {
        assertThatThrownBy(() -> insertEntry("2020-01-15T00:00:00Z")).hasMessageContaining("no partition of relation");
        assertThatThrownBy(() -> repository.saveForEvent(entry(), Instant.parse("2020-01-15T00:00:00Z"), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("no partition of relation");
        assertThatThrownBy(() -> repository.save(entry(), Instant.parse("2020-01-15T00:00:00Z"))).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.sql("select count(*) from " + ENTRIES).query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 a write into a month without a partition raises the ALERT and the failure is rethrown unchanged; a violated CHECK constraint raises no such ALERT")
    void onlyAMissingPartitionRaisesTheAlert() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.forcitis.one.platform.audit.infrastructure.persistence.JdbcAuditEntryRepository");
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            assertThatThrownBy(() -> repository.save(entry(), Instant.parse("2020-01-15T00:00:00Z"))).isInstanceOf(DataAccessException.class).hasMessageContaining("no partition of relation");
            assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).anyMatch(m -> m.startsWith("ALERT platform.audit late audit entry") && m.endsWith("no audit partition") && m.contains("2020-01-01"));

            logged.list.clear();
            AuditEntry emptyDetail = new AuditEntry("test.audit.partition", null, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, UUID.randomUUID()), "");
            assertThatThrownBy(() -> repository.save(emptyDetail, Instant.parse("2026-10-12T00:00:00Z"))).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_audit_entries_detail_length");
            assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("no audit partition"));
        } finally {
            logger.detachAppender(logged);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the partition function creates what is missing, skips what exists, adds the truncate trigger to each new partition, and returns the number created")
    void theFunctionCreatesTheMissingMonths() {
        assertThat(created("2026-10-01", "2026-12-01")).as("these exist since the baseline").isZero();
        assertThat(created(day(12, 1), day(14, 1))).isEqualTo(3L);
        assertThat(created(day(12, 1), day(14, 1))).isZero();

        assertThat(exists(partition(12))).isTrue();
        assertThat(jdbc.sql("""
                select count(*) from pg_trigger t where t.tgrelid = ('platform_audit.' || :partition)::regclass and t.tgname = 'audit_entries_reject_truncate'
                """).param("partition", partition(13)).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select pg_get_expr(c.relpartbound, c.oid) from pg_class c where c.oid = ('platform_audit.' || :partition)::regclass").param("partition", partition(13)).query(String.class).single())
                .contains(day(13, 1) + " 00:00:00+00").contains(day(14, 1) + " 00:00:00+00");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the partition function refuses a day that is not a month start, a reversed range, more than 24 months, and a month inside the archived position")
    void theFunctionRefusesBadArguments() {
        assertThatThrownBy(() -> created(day(15, 15), day(16, 1))).hasMessageContaining("first day of a month");
        assertThatThrownBy(() -> created(day(15, 1), day(16, 2))).hasMessageContaining("first day of a month");
        assertThatThrownBy(() -> created(day(16, 1), day(15, 1))).hasMessageContaining("is after through");
        assertThatThrownBy(() -> created(day(15, 1), day(39, 1))).hasMessageContaining("more than 24 months");
        assertThat(created(day(15, 1), day(38, 1))).as("24 months are allowed").isEqualTo(24L);
        assertThatThrownBy(() -> jdbc.sql("select platform_audit.create_audit_entry_partitions(null, cast(:m as date))").param("m", day(15, 1)).query(Long.class).single()).hasMessageContaining("both months are required");

        UUID e1 = insertEntry("2026-10-05T00:00:00Z");
        insertBatch(e1, "2026-10-05T00:00:00Z", e1, "2026-10-05T00:00:00Z", 1);
        assertThatThrownBy(() -> created("2026-03-01", "2026-03-01")).as("a month before the archived position").hasMessageContaining("already within the archived position")
                .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("55000"));
        assertThat(created("2026-10-01", "2026-12-01")).as("an existing partition is skipped, not refused").isZero();
        assertThat(exists("audit_entries_p202603")).isFalse();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 UPDATE, DELETE and TRUNCATE are refused through the table and directly on a partition")
    void theTrailIsAppendOnlyOnEveryPartition() {
        insertEntry("2026-10-10T00:00:00Z");

        for (String target : List.of(ENTRIES, "platform_audit.audit_entries_p202610")) {
            assertThatThrownBy(() -> jdbc.sql("update " + target + " set detail = 'x'").update()).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.sql("delete from " + target).update()).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.sql("truncate table " + target).update()).hasMessageContaining("append-only");
        }
        for (String partition : partitions()) {
            assertThatThrownBy(() -> jdbc.sql("truncate table platform_audit." + partition).update()).as(partition).hasMessageContaining("append-only");
        }
        assertThat(jdbc.sql("select count(*) from " + ENTRIES).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the drop function removes a month only when it is archived and checked: the rows are all within the position and equal the batches' count")
    void aCheckedMonthIsDropped() {
        created(day(16, 1), day(16, 1));
        UUID first = insertEntry(at(16, 3));
        UUID last = insertEntry(at(16, 20));
        insertBatch(first, at(16, 3), last, at(16, 20), 2);

        long dropped = jdbc.sql("select platform_audit.drop_archived_audit_entry_partition(cast(:m as date))").param("m", day(16, 1)).query(Long.class).single();

        assertThat(dropped).isEqualTo(2L);
        assertThat(exists(partition(16))).isFalse();
        assertThat(jdbc.sql("select count(*) from " + ENTRIES).query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the drop function refuses and drops nothing when the partition is missing, nothing is archived, a row lies after the position, or the counts differ")
    void anUncheckedMonthIsNotDropped() {
        created(day(17, 1), day(17, 1));
        assertThatThrownBy(() -> drop(day(17, 15))).hasMessageContaining("first day of a month");
        assertThatThrownBy(() -> drop("2019-01-01")).hasMessageContaining("does not exist");
        UUID first = insertEntry(at(17, 3));
        UUID second = insertEntry(at(17, 20));
        assertThatThrownBy(() -> drop(day(17, 1))).as("nothing archived").hasMessageContaining("nothing has been archived");

        insertBatch(first, at(17, 3), first, at(17, 3), 1);
        assertThatThrownBy(() -> drop(day(17, 1))).as("a row after the position").hasMessageContaining("after the archived position");

        insertBatch(second, at(17, 20), second, at(17, 20), 3);
        assertThatThrownBy(() -> drop(day(17, 1))).as("the batches count 4, the partition holds 2").hasMessageContaining("archived batches of the month hold 4");

        assertThat(exists(partition(17))).isTrue();
        assertThat(jdbc.sql("select count(*) from " + ENTRIES).query(Long.class).single()).isEqualTo(2L);
    }

    private void drop(String month) {
        jdbc.sql("select platform_audit.drop_archived_audit_entry_partition(cast(:m as date))").param("m", month).query(Long.class).single();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 neither function can be run by an account that was not granted it (PUBLIC has no EXECUTE)")
    void thePublicCannotExecuteTheFunctions() {
        jdbc.sql("create role audit_partition_probe nologin").update();
        try {
            for (String function : List.of("platform_audit.create_audit_entry_partitions(date, date)", "platform_audit.drop_archived_audit_entry_partition(date)")) {
                assertThat(jdbc.sql("select has_function_privilege('audit_partition_probe', '" + function + "', 'EXECUTE')").query(Boolean.class).single()).as(function).isFalse();
            }
        } finally {
            jdbc.sql("drop role audit_partition_probe").update();
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 an index on the table is built on every partition: the actor, action, subject and moment indexes exist, and the old names are gone")
    void theIndexesAreOnThePartitions() {
        List<String> indexes = jdbc.sql("select indexname from pg_indexes where schemaname = 'platform_audit' and tablename = 'audit_entries_p202610' order by 1").query(String.class).list();

        assertThat(indexes).anyMatch(i -> i.startsWith("audit_entries_p202610_actor_id_created_at"))
                .anyMatch(i -> i.contains("action_created_at")).anyMatch(i -> i.contains("subject_kind_subject_id_created_at")).anyMatch(i -> i.contains("created_at_id"));
        assertThat(jdbc.sql("select indexname from pg_indexes where schemaname = 'platform_audit' and tablename = 'audit_entries'").query(String.class).list())
                .contains("pk_audit_entries", "uk_audit_entries_event_id_created_at", "ix_audit_entries_actor_id_created_at", "ix_audit_entries_action_created_at",
                        "ix_audit_entries_subject_kind_subject_id_created_at", "ix_audit_entries_created_at_id")
                .doesNotContain("audit_entries_pkey", "audit_entries_action_idx", "audit_entries_actor_idx", "audit_entries_subject_idx", "uk_audit_entries_event_id");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-05 a text over 2000 characters or an empty text is refused in each text column, 2000 characters and a missing value are accepted")
    void textColumnsHaveLengthLimits() {
        for (String column : List.of("action", "detail", "change_reason", "before_text", "after_text")) {
            assertThatThrownBy(() -> insertText(column, "x".repeat(2001))).as(column + " 2001").hasMessageContaining("ck_audit_entries_" + column + "_length");
            assertThatThrownBy(() -> insertText(column, "")).as(column + " empty").hasMessageContaining("ck_audit_entries_" + column + "_length");
            insertText(column, "x".repeat(2000));
        }
        insertEntry("2026-10-12T00:00:00Z");
        assertThat(jdbc.sql("select count(*) from " + ENTRIES).query(Long.class).single()).isEqualTo(6L);
    }

    private void insertText(String column, String value) {
        String action = column.equals("action") ? ":value" : "'test.audit.text'";
        jdbc.sql("insert into platform_audit.audit_entries (id, created_at, updated_at, action, subject_kind, subject_id" + (column.equals("action") ? "" : ", " + column)
                + ") values (uuidv7(), cast('2026-10-12T00:00:00Z' as timestamptz), cast('2026-10-12T00:00:00Z' as timestamptz), " + action + ", 'USER_ACCOUNT', gen_random_uuid()"
                + (column.equals("action") ? "" : ", :value") + ")").param("value", value).update();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-05 a source address is 1 to 64 characters in the table; the application leaves a longer or non-address value empty and writes the entry")
    void theSourceAddressIsBounded() {
        assertThatThrownBy(() -> insertAddress("1".repeat(65))).hasMessageContaining("ck_audit_entries_source_address_length");
        assertThatThrownBy(() -> insertAddress("")).hasMessageContaining("ck_audit_entries_source_address_length");
        insertAddress("2001:db8::1");

        for (String bad : List.of("1".repeat(65), "unknown", "", "203.0.113.9, 10.0.0.1")) {
            repository.save(entry().withTrace(bad, com.forcitis.one.platform.audit.api.AuditOutcome.SUCCEEDED, "SSO"), Instant.parse("2026-10-13T00:00:00Z"));
        }
        repository.save(entry().withTrace("fe80::1%eth0", com.forcitis.one.platform.audit.api.AuditOutcome.SUCCEEDED, "SSO"), Instant.parse("2026-10-13T00:00:00Z"));

        assertThat(jdbc.sql("select count(*) from " + ENTRIES + " where created_at = '2026-10-13T00:00:00Z' and source_address is null").query(Long.class).single()).isEqualTo(4L);
        assertThat(jdbc.sql("select source_address from " + ENTRIES + " where source_address = 'fe80::1%eth0'").query(String.class).single()).isEqualTo("fe80::1%eth0");
    }

    private void insertAddress(String address) {
        jdbc.sql("insert into platform_audit.audit_entries (id, created_at, updated_at, action, subject_kind, subject_id, source_address) values (uuidv7(), cast('2026-10-12T00:00:00Z' as timestamptz), cast('2026-10-12T00:00:00Z' as timestamptz), 'a', 'USER_ACCOUNT', gen_random_uuid(), :a)")
                .param("a", address).update();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-09 an insert without created_at is refused: the moment comes from the caller's clock, not the database")
    void createdAtHasNoDefault() {
        assertThatThrownBy(() -> jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_kind, subject_id) values (uuidv7(), 'a', 'USER_ACCOUNT', gen_random_uuid())").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("no partition of relation");
        assertThat(jdbc.sql("select count(*) from information_schema.columns where table_schema = 'platform_audit' and table_name = 'audit_entries' and column_name = 'created_at' and column_default is not null")
                .query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the job has created the current month and the months ahead, and counts a failure when a month cannot be created")
    void theJobPreCreatesTheMonthsAhead() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        for (int ahead = 0; ahead <= 3; ahead++) {
            assertThat(exists("audit_entries_p" + now.plusMonths(ahead).toString().replace("-", ""))).as("month +" + ahead).isTrue();
        }

        UUID id = insertEntry("2026-10-05T00:00:00Z");
        insertBatch(id, "2026-10-05T00:00:00Z", id, at(100, 1), 1);
        var meters = new SimpleMeterRegistry();
        LocalDate far = now.plusMonths(40).atDay(15);
        new AuditPartitionJob(jdbc, Clock.fixed(far.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), new AuditPartitionJob.Policy(3), meters).ensurePartitions();

        assertThat(meters.counter("forcitis.platform.audit.partitions.failures").count()).as("the months of a far future are already within an archived position far beyond them").isEqualTo(1.0);
        Consumer<Integer> invalid = months -> assertThatThrownBy(() -> new AuditPartitionJob.Policy(months)).isInstanceOf(IllegalArgumentException.class);
        invalid.accept(null);
        invalid.accept(0);
        invalid.accept(-1);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the drop function waits for an uncommitted insert into the month and fails on the lock timeout; the row survives, and once it is committed the check refuses the drop")
    void aRowInFlightIsNeitherLostNorDropped() throws Exception {
        created(day(60, 1), day(60, 1));
        UUID archived = insertEntry(at(60, 3));
        insertBatch(archived, at(60, 3), archived, at(60, 3), 1);

        try (java.sql.Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try (var statement = writer.createStatement()) {
                statement.execute("insert into platform_audit.audit_entries (id, created_at, updated_at, action, subject_kind, subject_id) values (uuidv7(), '" + at(60, 10) + "', '" + at(60, 10) + "', 'test.audit.inflight', 'USER_ACCOUNT', gen_random_uuid())");

                assertThatThrownBy(() -> drop(day(60, 1))).rootCause().isInstanceOfSatisfying(java.sql.SQLException.class, e -> assertThat(e.getSQLState()).as("lock_not_available").isEqualTo("55P03"));
                statement.execute("lock table platform_audit.audit_entries in share update exclusive mode");
                assertThatThrownBy(() -> created(day(66, 1), day(66, 1))).as("a partition being created needs a lock that conflicts with the one held on the table, so it times out as well")
                        .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("55P03"));
                assertThat(exists(partition(66))).isFalse();
                writer.commit();
            }
        }

        assertThat(exists(partition(60))).isTrue();
        assertThat(jdbc.sql("select count(*) from " + ENTRIES + " where action = 'test.audit.inflight'").query(Long.class).single()).isEqualTo(1L);
        assertThatThrownBy(() -> drop(day(60, 1))).hasMessageContaining("after the archived position");
        assertThat(exists(partition(60))).isTrue();
        assertThat(jdbc.sql("select count(*) from " + ENTRIES + " where action = 'test.audit.inflight'").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 two instances creating the same month at the same time both succeed and the partition is created once")
    void concurrentCreationsCreateOnce() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Long>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return created(day(63, 1), day(63, 1));
                }));
            }
            start.countDown();
            long total = 0;
            for (var call : calls) {
                total += call.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
        assertThat(exists(partition(63))).isTrue();
    }
}
