package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Names;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.fixture.doubles.RecordingArchiveVault;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * R-1b P5 ADR-0033 section 1 item 10 and section 7 item 6 (card acceptance item 10, RULING-P5-D7): the audit archive does not depend
 * on how ids sort. The entries get ids that fall while their moments rise, so the entry written later has the smaller id; the
 * archive takes batches by (created_at, id), remembers its position as a moment and an id, and the reconciliation finds nothing missing.
 * The job is run the way the scheduler would (its run method), against a recording vault.
 */
@TestPropertySource(properties = "forcitis.platform.audit.archive.enabled=true")
@Import(AuditArchiveOrderIT.Vault.class)
class AuditArchiveOrderIT extends P5Support {

    static final String ACTION = "p5.archive.order";
    static final String JOB = "com.forcitis.one.platform.audit.infrastructure.archive.AuditArchiveJob";

    @TestConfiguration
    static class Vault {
        @Bean
        RecordingArchiveVault archiveVault() {
            return new RecordingArchiveVault();
        }
    }

    static final TestDatabase postgres = TestPostgres.database(AuditArchiveOrderIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    RecordingArchiveVault vault;

    /** Ids that fall: the n-th is a version 7 UUID of a moment n milliseconds before the fixed now. */
    private static UUID fallingId(int n) {
        long millis = NOW.toEpochMilli() - n;
        return new UUID((millis << 16) | 0x7000L, 0x8000000000000000L | n);
    }

    private UUID oldEntry(int n, Instant createdAt) throws Exception {
        UUID id = fallingId(n);
        try (var audit = schemaConnection(P5Names.AUDIT_SCHEMA); var insert = audit.prepareStatement("insert into audit_entries (id, action, "
                + P5Names.SUBJECT_KIND + ", " + P5Names.SUBJECT_ID + ", created_at) values (?, ?, 'USER_ACCOUNT', ?, ?)")) {
            insert.setObject(1, id);
            insert.setString(2, ACTION);
            insert.setObject(3, UUID.randomUUID());
            insert.setTimestamp(4, Timestamp.from(createdAt));
            insert.executeUpdate();
        }
        return id;
    }

    private void createPartitionOf(Instant moment) throws Exception {
        LocalDate month = YearMonth.from(moment.atOffset(ZoneOffset.UTC)).atDay(1);
        try (var audit = schemaConnection(P5Names.AUDIT_SCHEMA); var create = audit.prepareStatement("select platform_audit.create_audit_entry_partitions(?, ?)")) {
            create.setObject(1, month);
            create.setObject(2, month);
            create.execute();
        }
    }

    private void runTheJob() throws Exception {
        Object job = context.getBean(Class.forName(JOB));
        Method run = job.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(job);
    }

    private List<Map<String, Object>> batches() throws Exception {
        return auditRows("select first_entry_id, last_entry_id, entry_count, " + P5Names.FIRST_ENTRY_CREATED_AT + ", " + P5Names.LAST_ENTRY_CREATED_AT
                + " from audit_archive_batches order by archived_at, " + P5Names.LAST_ENTRY_CREATED_AT);
    }

    private long archivedActionLines() {
        return vault.lines().stream().filter(line -> line.contains("\"" + ACTION + "\"")).count();
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 1 项第 10 条：审计归档不依赖 ID 次序——递减 ID、时刻递增的到期条目全部进批次，批次记首末条时刻，水位是最后创建的那条（不是最大 ID），对账无差异；之后写入的 ID 更小而时刻更晚的到期条目下一轮也归档")
    void theArchiveFollowsMomentsNotIds() throws Exception {
        Instant first = NOW.minus(Duration.ofDays(1300));
        // ADR-0048: the partition of the old month must exist; the five moments (days 1 to 6 after first) all fall in one UTC month, so batches do not split
        assertThat(YearMonth.from(first.plus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC))).isEqualTo(YearMonth.from(first.plus(Duration.ofDays(6)).atOffset(ZoneOffset.UTC)));
        createPartitionOf(first.plus(Duration.ofDays(1)));
        List<UUID> ids = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            ids.add(oldEntry(n, first.plus(Duration.ofDays(n))));
        }
        assertThat(ids).as("the premise: the ids fall while the moments rise").isSortedAccordingTo((a, b) -> b.toString().compareTo(a.toString()));
        int mark = logMark();

        runTheJob();

        assertThat(archivedActionLines()).as("every due entry is in the archive").isEqualTo(5);
        for (UUID id : ids) {
            assertThat(vault.lines()).anyMatch(line -> line.contains("\"id\":\"" + id + "\""));
        }
        List<Map<String, Object>> batches = batches();
        assertThat(batches).hasSize(1);
        assertThat(((Number) batches.getFirst().get("entry_count")).intValue()).isEqualTo(5);
        assertThat(batches.getFirst().get("first_entry_id")).as("the first entry of the batch is the first created").isEqualTo(ids.getFirst());
        assertThat(batches.getFirst().get("last_entry_id")).as("the position is the last created entry, which has the smallest id").isEqualTo(ids.getLast());
        assertThat(((Timestamp) batches.getFirst().get(P5Names.FIRST_ENTRY_CREATED_AT)).toInstant()).isEqualTo(first.plus(Duration.ofDays(1)));
        assertThat(((Timestamp) batches.getFirst().get(P5Names.LAST_ENTRY_CREATED_AT)).toInstant()).isEqualTo(first.plus(Duration.ofDays(5)));
        assertThat(alertsSince(mark)).as("the reconciliation finds no difference").isEmpty();

        UUID later = oldEntry(6, first.plus(Duration.ofDays(6)));
        assertThat(later.toString()).isLessThan(ids.getLast().toString());
        runTheJob();

        assertThat(archivedActionLines()).as("the late entry with the smallest id is archived too").isEqualTo(6);
        assertThat(vault.lines()).anyMatch(line -> line.contains("\"id\":\"" + later + "\""));
        assertThat(batches()).hasSize(2);
        assertThat(alertsSince(mark)).isEmpty();
    }
}
