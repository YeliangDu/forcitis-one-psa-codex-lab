package com.forcitis.one.platform.audit.infrastructure.archive;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.RecordingArchiveVault;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.audit.archive.enabled=true"})
@Import(AuditArchiveJobIT.Doubles.class)
class AuditArchiveJobIT {
    @TestConfiguration
    static class Doubles {
        @Bean
        RecordingArchiveVault vault() {
            return new RecordingArchiveVault();
        }
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(AuditArchiveJobIT.class, TestPostgres.MIGRATED);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    AuditArchiveJob job;

    @Autowired
    RecordingArchiveVault vault;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meters;

    @BeforeEach
    void empty() {
        vault.reset();
        jdbc.sql("alter table platform_audit.audit_archive_batches disable trigger tg_audit_archive_batches_append_only").update();
        jdbc.sql("alter table platform_audit.audit_archive_batches disable trigger tg_audit_archive_batches_append_only_truncate").update();
        jdbc.sql("truncate table platform_audit.audit_archive_batches restart identity").update();
        partitionsForTheOldMonths();
        disableAppendOnlyTriggers();
        jdbc.sql("truncate table platform_audit.audit_entries restart identity").update();
    }

    /** The entries here are years old and the table has no catch-all partition, so the months they fall in are created first, as the application does for the months ahead. */
    private void partitionsForTheOldMonths() {
        LocalDate end = BusinessDay.utcMonth().atDay(1);
        for (LocalDate from = end.minusMonths(47); !from.isAfter(end); from = from.plusMonths(24)) {
            LocalDate through = from.plusMonths(23).isAfter(end) ? end : from.plusMonths(23);
            jdbc.sql("select platform_audit.create_audit_entry_partitions(:from, :through)").param("from", from).param("through", through).query(Integer.class).single();
        }
    }

    /** A statement trigger is not cloned to a partition, so the append-only triggers are switched off on the table and on every partition. */
    private void disableAppendOnlyTriggers() {
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_update_delete").update();
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_truncate").update();
        for (String partition : jdbc.sql("select c.relname from pg_inherits i join pg_class c on c.oid = i.inhrelid where i.inhparent = 'platform_audit.audit_entries'::regclass").query(String.class).list()) {
            jdbc.sql("alter table platform_audit." + partition + " disable trigger audit_entries_reject_update_delete").update();
            jdbc.sql("alter table platform_audit." + partition + " disable trigger audit_entries_reject_truncate").update();
        }
    }

    private UUID entry(String action, String createdAgo) {
        return jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_kind, subject_id, created_at) values (uuidv7(), :action, 'USER_ACCOUNT', gen_random_uuid(), now() - cast(:ago as interval)) returning id")
                .param("action", action).param("ago", createdAgo).query(UUID.class).single();
    }

    /** Two entries that a test expects in one batch share a month (a batch never spans two months), whatever day the test runs on. */
    private UUID entryInOldMonth(String action, int day) {
        return jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_kind, subject_id, created_at) values (uuidv7(), :action, 'USER_ACCOUNT', gen_random_uuid(), date_trunc('month', now() at time zone 'UTC' - interval '40 months') at time zone 'UTC' + make_interval(days => :day)) returning id")
                .param("action", action).param("day", day).query(UUID.class).single();
    }

    private long batches() {
        return jdbc.sql("select count(*) from platform_audit.audit_archive_batches").query(Long.class).single();
    }

    @Test
    @DisplayName("T3 entries older than 3 years are archived in (created_at, id) order and recorded as a batch; younger ones wait; nothing is deleted online")
    void archivesOnlyWhatIsOldEnough() {
        UUID first = entryInOldMonth("old.one", 3);
        UUID second = entryInOldMonth("old.two", 5);
        entry("young", "10 days");

        job.run();

        assertThat(vault.lines()).hasSize(2);
        assertThat(vault.lines().getFirst()).contains("old.one").contains("\"id\":\"" + first + "\"");
        assertThat(batches()).isEqualTo(1);
        assertThat(jdbc.sql("select last_entry_id from platform_audit.audit_archive_batches").query(UUID.class).single()).isEqualTo(second);
        assertThat(jdbc.sql("select count(*) from platform_audit.audit_entries").query(Long.class).single()).isEqualTo(3L);

        job.run();
        assertThat(vault.batches).as("nothing new is due").hasSize(1);
    }

    @Test
    @DisplayName("T3 the archive carries the columns listed as ARCHIVE, including event id and correlation id, and nothing else")
    void archivedColumnsAreTheListedOnes() {
        jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_kind, subject_id, event_id, correlation_id, created_at) values (uuidv7(), 'old.one', 'USER_ACCOUNT', gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), now() - interval '1200 days')").update();

        job.run();

        String line = vault.lines().getFirst();
        assertThat(AuditArchiveJob.COLUMNS).allSatisfy(column -> assertThat(line).contains("\"" + column + "\":"));
        assertThat(line).doesNotContain("\"updated_at\"").doesNotContain("\"event_id\":null").doesNotContain("\"correlation_id\":null");
    }

    @Test
    @DisplayName("T3 an old entry written after a younger one is not skipped: the archive goes by moment, so it takes the old one at once and the younger follows once due")
    void aLateOldEntryIsNotLost() {
        UUID young = entry("young.first", "10 days");
        entry("old.later", "1200 days");

        job.run();
        assertThat(vault.lines()).as("only the entry that is old enough, although it was written after the younger one").singleElement().asString().contains("old.later");

        jdbc.sql("update platform_audit.audit_entries set created_at = now() - interval '1100 days' where id = :id").param("id", young).update();
        job.run();

        assertThat(vault.lines()).hasSize(2);
    }

    @Test
    @DisplayName("L-2 an old entry that appears before the archived position is never archived; the reconciliation counts it and raises the alarm")
    void aMissedEntryIsReported() {
        entry("old.one", "1200 days");
        entry("old.two", "1150 days");
        double before = meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count();

        job.run();
        assertThat(meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count()).as("every entry up to the position is in a batch").isEqualTo(before);

        jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_kind, subject_id, created_at) values (:id, 'old.late', 'USER_ACCOUNT', gen_random_uuid(), now() - interval '1300 days')")
                .param("id", new UUID(0x0000_0000_0000_7000L, 0x8000_0000_0000_0001L)).update();
        job.run();

        assertThat(vault.lines()).as("the late entry is not in the archive").hasSize(2);
        assertThat(meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("T3 when the vault fails no batch is recorded, so the next run archives the same entries again")
    void aFailedBatchIsRetried() {
        entry("old.one", "1200 days");
        vault.failure = new IllegalStateException("upload failed");

        assertThatThrownBy(() -> job.run()).hasMessageContaining("upload failed");
        assertThat(batches()).isZero();

        vault.failure = null;
        job.run();
        assertThat(batches()).isEqualTo(1);
    }

    @Test
    @DisplayName("T3 the archive progress cannot be rewritten: a batch row refuses update, delete and truncate")
    void progressIsAppendOnly() {
        entry("old.one", "1200 days");
        job.run();
        jdbc.sql("alter table platform_audit.audit_archive_batches enable trigger tg_audit_archive_batches_append_only").update();
        jdbc.sql("alter table platform_audit.audit_archive_batches enable trigger tg_audit_archive_batches_append_only_truncate").update();

        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_archive_batches").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_archive_batches set entry_count = 9").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table platform_audit.audit_archive_batches").update()).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 a batch never spans two UTC months: entries of two months become two batches although both are due")
    void aBatchStaysInOneMonth() {
        entry("old.first.month", "1300 days");
        entry("old.second.month", "1200 days");

        job.run();

        assertThat(batches()).isEqualTo(2);
        assertThat(jdbc.sql("""
                select count(*) from platform_audit.audit_archive_batches
                 where to_char(first_entry_created_at at time zone 'UTC', 'YYYYMM') <> to_char(last_entry_created_at at time zone 'UTC', 'YYYYMM')
                """).query(Long.class).single()).isZero();
        assertThat(vault.batches).hasSize(2);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-07 the reconciliation compares only the months that are still online: a month whose partition was removed after its archive check raises no alarm")
    void aRemovedMonthIsNotReconciled() {
        entry("old.first.month", "1300 days");
        entry("old.second.month", "1200 days");
        double before = meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count();
        job.run();
        assertThat(meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count()).isEqualTo(before);

        String month = jdbc.sql("select to_char(min(created_at) at time zone 'UTC', 'YYYY-MM-01') from platform_audit.audit_entries").query(String.class).single();
        assertThat(jdbc.sql("select platform_audit.drop_archived_audit_entry_partition(cast(:m as date))").param("m", month).query(Long.class).single()).isEqualTo(1L);
        job.run();

        assertThat(jdbc.sql("select count(*) from platform_audit.audit_entries").query(Long.class).single()).isEqualTo(1L);
        assertThat(meters.counter("forcitis.platform.audit.archive.reconciliation.failures").count()).as("one archived entry is gone with its month, the rest still matches").isEqualTo(before);
    }
}
