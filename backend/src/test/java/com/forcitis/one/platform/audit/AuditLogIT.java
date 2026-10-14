package com.forcitis.one.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Testcontainers
class AuditLogIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    static final String ENTRIES = "/api/v1/platform/audit-entries";
    static final RequestPostProcessor READER = user("7").authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);

    @Autowired
    AuditLog audit;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long count(String subjectKey) {
        return jdbc.sql("select count(*) from platform_audit.audit_entries where subject_key = :subject").param("subject", subjectKey)
                .query(Long.class).single();
    }

    private void insertAt(String createdAt, String action, Long actor, String subjectKey) {
        jdbc.sql("""
                insert into platform_audit.audit_entries (created_at, updated_at, action, actor_id, subject_key, detail)
                values (cast(:at as timestamptz), cast(:at as timestamptz), :action, :actor, :subject, 'synthetic')
                """).param("at", createdAt).param("action", action).param("actor", actor, java.sql.Types.BIGINT).param("subject", subjectKey).update();
    }

    @Test
    @DisplayName("record joins the caller's transaction: it exists only if the business change commits")
    void recordFollowsTheCallersTransaction() {
        String committed = unique("subject");
        String rolledBack = unique("subject");

        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", new UserId(5), null, committed, "kept")));
        tx.executeWithoutResult(s -> {
            audit.record(new AuditEntry("platform.audit.test.recorded", new UserId(5), null, rolledBack, "gone"));
            s.setRollbackOnly();
        });

        assertThat(count(committed)).isEqualTo(1);
        assertThat(count(rolledBack)).isZero();
    }

    @Test
    @DisplayName("recordIndependently survives the caller's rollback (failed logins, denied actions)")
    void independentRecordSurvivesRollback() {
        String independent = unique("subject");
        String joined = unique("subject");

        tx.executeWithoutResult(s -> {
            audit.recordIndependently(new AuditEntry("platform.audit.test.denied", null, null, independent, "kept"));
            audit.record(new AuditEntry("platform.audit.test.denied", null, null, joined, "gone"));
            s.setRollbackOnly();
        });

        assertThat(count(independent)).isEqualTo(1);
        assertThat(count(joined)).isZero();
    }

    @Test
    @DisplayName("G34 the table is append-only: UPDATE, DELETE and TRUNCATE are refused by the database")
    void tableRefusesChanges() {
        String subject = unique("subject");
        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", new UserId(5), null, subject, "original")));

        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_entries set detail = 'changed' where subject_key = :s").param("s", subject).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_entries where subject_key = :s").param("s", subject).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table platform_audit.audit_entries").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        assertThat(jdbc.sql("select detail from platform_audit.audit_entries where subject_key = :s").param("s", subject).query(String.class).single())
                .isEqualTo("original");
    }

    @Test
    @DisplayName("search by actor, action, subject key and time range, newest first, one page at a time")
    void searchFiltersAndPages() throws Exception {
        String subject = unique("subject");
        String action = unique("platform.audit.test");
        insertAt("2026-01-01T08:00:00Z", action, 41L, subject);
        insertAt("2026-01-02T08:00:00Z", action, 41L, subject);
        insertAt("2026-01-03T08:00:00Z", action, 42L, subject);
        insertAt("2026-01-04T08:00:00Z", action, null, subject);
        insertAt("2026-01-05T08:00:00Z", unique("platform.audit.other"), 41L, subject);

        mvc.perform(get(ENTRIES).param("subjectKey", subject).param("action", action).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchCount").value(4))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-04T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.items[3].actorId").value("41"))
                .andExpect(jsonPath("$.items[3].subjectKey").value(subject));
        mvc.perform(get(ENTRIES).param("subjectKey", subject).param("actorId", "41").with(READER))
                .andExpect(jsonPath("$.matchCount").value(3));
        mvc.perform(get(ENTRIES).param("subjectKey", subject).param("action", action).param("actorId", "42").with(READER))
                .andExpect(jsonPath("$.matchCount").value(1));
        mvc.perform(get(ENTRIES).param("subjectKey", subject).param("from", "2026-01-02T00:00:00Z").param("to", "2026-01-04T00:00:00Z").with(READER))
                .andExpect(jsonPath("$.matchCount").value(2))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-03T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-01-02T08:00:00Z"));
        mvc.perform(get(ENTRIES).param("subjectKey", subject).param("action", action).param("page", "1").param("limit", "3").with(READER))
                .andExpect(jsonPath("$.matchCount").value(4))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-01T08:00:00Z"));
        mvc.perform(get(ENTRIES).param("subjectKey", unique("subject")).with(READER))
                .andExpect(jsonPath("$.matchCount").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("a recorded entry is found by its subject key with the actor and the proxied person")
    void recordedEntryIsSearchable() throws Exception {
        String subject = unique("subject");
        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", new UserId(51), new UserId(52), subject, "by proxy")));

        mvc.perform(get(ENTRIES).param("subjectKey", subject).with(READER))
                .andExpect(jsonPath("$.items[0].action").value("platform.audit.test.recorded"))
                .andExpect(jsonPath("$.items[0].actorId").value("51"))
                .andExpect(jsonPath("$.items[0].onBehalfOfId").value("52"))
                .andExpect(jsonPath("$.items[0].detail").value("by proxy"));
    }

    @Test
    @DisplayName("searching needs audit-entry:read; anonymous callers get 401 and other permissions 403")
    void searchNeedsThePermission() throws Exception {
        mvc.perform(get(ENTRIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(ENTRIES).with(user("8").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ))).andExpect(status().isForbidden());
        mvc.perform(get(ENTRIES).with(READER)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unusable page, limit or time range is refused with 400")
    void badPagingIsRefused() throws Exception {
        mvc.perform(get(ENTRIES).param("limit", "0").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("limit", "201").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("page", "-1").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("from", "2026-02-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z").with(READER))
                .andExpect(status().isBadRequest());
    }
}
