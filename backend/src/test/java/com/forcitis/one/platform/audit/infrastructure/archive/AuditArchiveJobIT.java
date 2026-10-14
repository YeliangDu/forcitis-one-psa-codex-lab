package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.RecordingArchiveVault;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.audit.archive.enabled=true"})
@Testcontainers
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    AuditArchiveJob job;

    @Autowired
    RecordingArchiveVault vault;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void empty() {
        vault.reset();
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_update_delete").update();
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_truncate").update();
        jdbc.sql("alter table platform_audit.audit_archive_batches disable trigger tg_audit_archive_batches_append_only").update();
        jdbc.sql("alter table platform_audit.audit_archive_batches disable trigger tg_audit_archive_batches_append_only_truncate").update();
        jdbc.sql("truncate table platform_audit.audit_entries, platform_audit.audit_archive_batches restart identity").update();
    }

    private UUID entry(String action, String createdAgo) {
        return jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_key, created_at) values (uuidv7(), :action, 'x:1', now() - cast(:ago as interval)) returning id")
                .param("action", action).param("ago", createdAgo).query(UUID.class).single();
    }

    private long batches() {
        return jdbc.sql("select count(*) from platform_audit.audit_archive_batches").query(Long.class).single();
    }

    @Test
    @DisplayName("T3 entries older than 3 years are archived in id order and recorded as a batch; younger ones wait; nothing is deleted online")
    void archivesOnlyWhatIsOldEnough() {
        UUID first = entry("old.one", "1200 days");
        UUID second = entry("old.two", "1150 days");
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
        jdbc.sql("insert into platform_audit.audit_entries (id, action, subject_key, event_id, correlation_id, created_at) values (uuidv7(), 'old.one', 'x:1', gen_random_uuid(), gen_random_uuid(), now() - interval '1200 days')").update();

        job.run();

        String line = vault.lines().getFirst();
        assertThat(AuditArchiveJob.COLUMNS).allSatisfy(column -> assertThat(line).contains("\"" + column + "\":"));
        assertThat(line).doesNotContain("\"updated_at\"").doesNotContain("\"event_id\":null").doesNotContain("\"correlation_id\":null");
    }

    @Test
    @DisplayName("T3 an old entry written after a younger one is not skipped: only a prefix of ids that are all old enough is archived, the rest follows once due")
    void aLateOldEntryIsNotLost() {
        UUID young = entry("young.first", "10 days");
        entry("old.later", "1200 days");

        job.run();
        assertThat(vault.batches).isEmpty();

        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_update_delete").update();
        jdbc.sql("update platform_audit.audit_entries set created_at = now() - interval '1100 days' where id = :id").param("id", young).update();
        job.run();

        assertThat(vault.lines()).hasSize(2);
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
}
