package com.forcitis.one.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** ADR-0069 decision 14: the kinds live in a registry table only a migration writes; a registered kind is never rewritten or deleted, a retirement is one recorded fact. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class AuditSubjectKindRegistryIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityDirectory legalEntityDirectory;

    static final TestDatabase postgres = TestPostgres.database(AuditSubjectKindRegistryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    JdbcClient jdbc;

    private static final String INSERT = "insert into platform_audit.audit_subject_kinds (audit_subject_kind_id, code, owner_bc_code, has_object, status, effective_from_date, retired_date, created_at, updated_at)"
            + " values (uuidv7(), '%s', 'platform.audit', true, '%s', DATE '2026-01-01', %s, now(), now())";

    @Test
    @DisplayName("the twenty-two seeded kinds are there, SYNC_RUN is RETIRED on 2026-11-02 (#934), and audit_entries.subject_kind is a foreign key to the registry code")
    void seedsAndForeignKey() {
        assertThat(jdbc.sql("select count(*) from platform_audit.audit_subject_kinds where audit_subject_kind_id::text like '019a0000-0000-7000-8000-000000000%'").query(Long.class).single()).isEqualTo(22L);
        assertThat(jdbc.sql("select status || ' ' || retired_date from platform_audit.audit_subject_kinds where code = 'SYNC_RUN'").query(String.class).single())
                .isEqualTo("RETIRED 2026-11-02");
        assertThat(jdbc.sql("select count(*) from pg_constraint where conname = 'fk_audit_entries_subject_kind' and contype = 'f' and conrelid = 'platform_audit.audit_entries'::regclass and confrelid = 'platform_audit.audit_subject_kinds'::regclass")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a new kind is one inserted row; the status and the retirement date go together")
    void aNewKindIsOneRow() {
        jdbc.sql(INSERT.formatted("PROBE_ONE", "ACTIVE", "null")).update();

        assertThat(jdbc.sql("select count(*) from platform_audit.audit_subject_kinds where code = 'PROBE_ONE'").query(Long.class).single()).isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.sql(INSERT.formatted("PROBE_TWO", "RETIRED", "null")).update()).hasMessageContaining("ck_audit_subject_kinds_retired_matches_status");
        assertThatThrownBy(() -> jdbc.sql(INSERT.formatted("PROBE_THREE", "ACTIVE", "DATE '2026-06-01'")).update()).hasMessageContaining("ck_audit_subject_kinds_retired_matches_status");
        assertThatThrownBy(() -> jdbc.sql(INSERT.formatted("probe_four", "ACTIVE", "null")).update()).hasMessageContaining("ck_audit_subject_kinds_code");
    }

    @Test
    @DisplayName("a registered kind is never deleted or rewritten; its retirement is recorded once as a pair of status and date")
    void retirementIsOneRecordedFact() {
        jdbc.sql(INSERT.formatted("PROBE_FIVE", "ACTIVE", "null")).update();

        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_subject_kinds where code = 'PROBE_FIVE'").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate platform_audit.audit_subject_kinds cascade").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_subject_kinds set code = 'PROBE_SIX' where code = 'PROBE_FIVE'").update()).hasMessageContaining("cannot be rewritten");
        assertThat(jdbc.sql("update platform_audit.audit_subject_kinds set status = 'RETIRED', retired_date = DATE '2026-06-01' where code = 'PROBE_FIVE'").update()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_subject_kinds set retired_date = DATE '2026-07-01' where code = 'PROBE_FIVE'").update()).hasMessageContaining("set once");
        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_subject_kinds set status = 'ACTIVE', retired_date = null where code = 'PROBE_FIVE'").update()).hasMessageContaining("set once");
    }

    @Test
    @DisplayName("the kinds ck_audit_entries_subject_id lists (no object) are exactly the registered kinds with has_object false")
    void noObjectKindsMatchTheSubjectIdCheck() {
        String definition = jdbc.sql("select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_audit_entries_subject_id' and conrelid = 'platform_audit.audit_entries'::regclass")
                .query(String.class).single();
        java.util.Set<String> listed = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(definition);
        while (m.find()) {
            listed.add(m.group(1));
        }
        java.util.Set<String> registered = new java.util.TreeSet<>(jdbc.sql("select code from platform_audit.audit_subject_kinds where has_object = false and audit_subject_kind_id::text like '019a0000-0000-7000-8000-000000000%'")
                .query(String.class).list());

        assertThat(listed).isNotEmpty().isEqualTo(registered);
    }
}
