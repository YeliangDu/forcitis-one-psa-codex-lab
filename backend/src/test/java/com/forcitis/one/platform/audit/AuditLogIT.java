package com.forcitis.one.platform.audit;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class AuditLogIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(AuditLogIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String ENTRIES = "/api/v1/platform/audit-entries";
    static final RequestPostProcessor READER = user(TestAccounts.account(7).value().toString()).authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);

    @Autowired
    tools.jackson.databind.json.JsonMapper json;

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

    private static AuditSubject subj(UUID id) {
        return AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, id);
    }

    private long count(UUID subjectId) {
        return jdbc.sql("select count(*) from platform_audit.audit_entries where subject_id = :subject").param("subject", subjectId)
                .query(Long.class).single();
    }

    private void insertAt(String createdAt, String action, Long actor, UUID subjectId) {
        jdbc.sql("""
                insert into platform_audit.audit_entries (id, created_at, updated_at, action, actor_id, subject_kind, subject_id, detail)
                values (uuidv7(), cast(:at as timestamptz), cast(:at as timestamptz), :action, :actor, 'USER_ACCOUNT', :subject, 'synthetic')
                """).param("at", createdAt).param("action", action).param("actor", actor == null ? null : TestAccounts.account(actor).value(), java.sql.Types.OTHER).param("subject", subjectId).update();
    }

    @Test
    @DisplayName("record joins the caller's transaction: it exists only if the business change commits")
    void recordFollowsTheCallersTransaction() {
        UUID committed = UUID.randomUUID();
        UUID rolledBack = UUID.randomUUID();

        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", TestAccounts.account(5), null, subj(committed), "kept")));
        tx.executeWithoutResult(s -> {
            audit.record(new AuditEntry("platform.audit.test.recorded", TestAccounts.account(5), null, subj(rolledBack), "gone"));
            s.setRollbackOnly();
        });

        assertThat(count(committed)).isEqualTo(1);
        assertThat(count(rolledBack)).isZero();
    }

    @Test
    @DisplayName("C-6: record returns the id of the entry it wrote, so the caller can keep it in the same transaction")
    void recordReturnsTheIdOfTheWrittenEntry() {
        UUID subject = UUID.randomUUID();

        AuditEntryId id = tx.execute(s -> audit.record(
                new AuditEntry("platform.audit.test.recorded", TestAccounts.account(5), null, subj(subject), "returned")));

        assertThat(id).isNotNull();
        assertThat(jdbc.sql("select id from platform_audit.audit_entries where subject_id = :subject").param("subject", subject)
                .query(UUID.class).single()).isEqualTo(id.value());
    }

    @Test
    @DisplayName("record outside a transaction is refused: an audit entry is never written on its own by accident")
    void recordNeedsTheCallersTransaction() {
        assertThatThrownBy(() -> audit.record(new AuditEntry("platform.audit.test.recorded", null, null, subj(UUID.randomUUID()), "x")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("recordIndependently survives the caller's rollback (failed logins, denied actions)")
    void independentRecordSurvivesRollback() {
        UUID independent = UUID.randomUUID();
        UUID joined = UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            audit.recordIndependently(new AuditEntry("platform.audit.test.denied", null, null, subj(independent), "kept"));
            audit.record(new AuditEntry("platform.audit.test.denied", null, null, subj(joined), "gone"));
            s.setRollbackOnly();
        });

        assertThat(count(independent)).isEqualTo(1);
        assertThat(count(joined)).isZero();
    }

    @Test
    @DisplayName("G34 the table is append-only: UPDATE, DELETE and TRUNCATE are refused by the database")
    void tableRefusesChanges() {
        UUID subject = UUID.randomUUID();
        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", TestAccounts.account(5), null, subj(subject), "original")));

        assertThatThrownBy(() -> jdbc.sql("update platform_audit.audit_entries set detail = 'changed' where subject_id = :s").param("s", subject).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_audit.audit_entries where subject_id = :s").param("s", subject).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table platform_audit.audit_entries").update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        assertThat(jdbc.sql("select detail from platform_audit.audit_entries where subject_id = :s").param("s", subject).query(String.class).single())
                .isEqualTo("original");
    }

    @Test
    @DisplayName("search by actor, action, subject id and time range, newest first, one page at a time")
    void searchFiltersAndPages() throws Exception {
        UUID subject = UUID.randomUUID();
        String action = unique("platform.audit.test");
        jdbc.sql("select platform_audit.create_audit_entry_partitions(date '2026-01-01', date '2026-01-01')").query(Integer.class).single();
        insertAt("2026-01-01T08:00:00Z", action, 41L, subject);
        insertAt("2026-01-02T08:00:00Z", action, 41L, subject);
        insertAt("2026-01-03T08:00:00Z", action, 42L, subject);
        insertAt("2026-01-04T08:00:00Z", action, null, subject);
        insertAt("2026-01-05T08:00:00Z", unique("platform.audit.other"), 41L, subject);

        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("action", action).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-04T08:00:00Z"))
                .andExpect(jsonPath("$.items[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.items[3].actorId").value(TestAccounts.account(41).value().toString()))
                .andExpect(jsonPath("$.items[3].subjectId").value(subject.toString()));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("actorId", TestAccounts.account(41).value().toString()).with(READER))
                .andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("action", action).param("actorId", TestAccounts.account(42).value().toString()).with(READER))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("from", "2026-01-02T00:00:00Z").param("to", "2026-01-04T00:00:00Z").with(READER))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-03T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-01-02T08:00:00Z"));
        String cursor = json.readTree(mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("action", action).param("limit", "3").with(READER))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].createdAt").value("2026-01-02T08:00:00Z"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString()).path("nextCursor").asString();
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("action", action).param("limit", "3").param("cursor", cursor).with(READER))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-01-01T08:00:00Z"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", UUID.randomUUID().toString()).with(READER))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("a recorded entry is found by its subject id with the actor and the proxied person")
    void recordedEntryIsSearchable() throws Exception {
        UUID subject = UUID.randomUUID();
        tx.executeWithoutResult(s -> audit.record(new AuditEntry("platform.audit.test.recorded", TestAccounts.account(51), TestAccounts.account(52), subj(subject), "by proxy")));

        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).with(READER))
                .andExpect(jsonPath("$.items[0].action").value("platform.audit.test.recorded"))
                .andExpect(jsonPath("$.items[0].actorId").value(TestAccounts.account(51).value().toString()))
                .andExpect(jsonPath("$.items[0].onBehalfOfId").value(TestAccounts.account(52).value().toString()))
                .andExpect(jsonPath("$.items[0].detail").value("by proxy"));
    }

    @Test
    @DisplayName("searching needs audit-entry:read; anonymous callers get 401 and other permissions 403")
    void searchNeedsThePermission() throws Exception {
        mvc.perform(get(ENTRIES)).andExpect(status().isUnauthorized());
        mvc.perform(get(ENTRIES).with(user(TestAccounts.account(8).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ))).andExpect(status().isForbidden());
        mvc.perform(get(ENTRIES).with(READER)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unusable page, limit or time range is refused with 400")
    void badPagingIsRefused() throws Exception {
        mvc.perform(get(ENTRIES).param("limit", "0").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("limit", "201").with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("cursor", "not-a-cursor").with(READER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_CURSOR_INVALID"));
        mvc.perform(get(ENTRIES).param("from", "2026-02-01T00:00:00Z").param("to", "2026-01-01T00:00:00Z").with(READER))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("subjectId", UUID.randomUUID().toString()).with(READER)).andExpect(status().isBadRequest());
        mvc.perform(get(ENTRIES).param("subjectKind", "REQUEST").param("subjectId", UUID.randomUUID().toString()).with(READER))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("D-1a-170/171 an entry carries source address, outcome, login method, reason and before / after; they can be searched by on-behalf-of, address, outcome and action prefix")
    void traceColumnsAreStoredAndSearchable() throws Exception {
        UUID subject = UUID.randomUUID();
        String prefix = unique("platform.audit.trace");
        tx.executeWithoutResult(s -> {
            audit.record(new AuditEntry(prefix + ".changed", TestAccounts.account(61), TestAccounts.account(62), subj(subject), "d")
                    .withTrace("10.1.2.3", com.forcitis.one.platform.audit.api.AuditOutcome.SUCCEEDED, "SSO").withChange("quarterly review", "ACTIVE", "INACTIVE"));
            audit.record(new AuditEntry(prefix + ".refused", TestAccounts.account(61), null, subj(subject), "d")
                    .withTrace("10.9.9.9", com.forcitis.one.platform.audit.api.AuditOutcome.FAILED, null));
        });

        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("actionPrefix", prefix).with(READER))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("onBehalfOfId", TestAccounts.account(62).value().toString()).with(READER))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sourceAddress").value("10.1.2.3"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].loginMethod").value("SSO"))
                .andExpect(jsonPath("$.items[0].changeReason").value("quarterly review"))
                .andExpect(jsonPath("$.items[0].beforeText").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].afterText").value("INACTIVE"));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("outcome", "FAILED").with(READER))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].sourceAddress").value("10.9.9.9"));
        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).param("sourceAddress", "10.9.9.9").with(READER))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(ENTRIES).param("outcome", "MAYBE").with(READER)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("B1-M9 looking into the audit trail is an audit entry of its own: who, with which filter, how many were returned (intent 152)")
    void searchesAreAudited() throws Exception {
        UUID subject = UUID.randomUUID();

        mvc.perform(get(ENTRIES).param("subjectKind", "USER_ACCOUNT").param("subjectId", subject.toString()).with(READER)).andExpect(status().isOk());

        assertThat(jdbc.sql("select actor_id || '|' || detail from platform_audit.audit_entries where action = 'platform.audit.audit-entry.searched' and detail like :d")
                .param("d", "%subjectId=" + subject + "%").query(String.class).single()).startsWith(TestAccounts.account(7).value() + "|returned=0 filter=subjectKind=USER_ACCOUNT,subjectId=" + subject);
    }

    @Test
    @DisplayName("D-1a-170 a secret or personal data must not get into an audit text: the entry is refused when it is built")
    void secretsAreRefused() {
        assertThatThrownBy(() -> new AuditEntry("platform.audit.test.x", null, null, subj(UUID.randomUUID()), "login with password=hunter2"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry("platform.audit.test.x", null, null, subj(UUID.randomUUID()), "d").withChange("because", "token: abc", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
