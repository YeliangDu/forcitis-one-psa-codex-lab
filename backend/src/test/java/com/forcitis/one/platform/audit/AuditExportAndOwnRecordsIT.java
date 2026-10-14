package com.forcitis.one.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.audit.export.max-rows=5"})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("OwnLoginRecords and the audit export: ADR-0027 section 3, D-1a-172")
class AuditExportAndOwnRecordsIT {
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

    static final String OWN = "/api/v1/platform/own-login-records";
    static final String EXPORT = "/api/v1/platform/audit-entries:export";
    static final RequestPostProcessor EMPLOYEE = user("1001").authorities(() -> Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ);
    static final RequestPostProcessor ADMINISTRATOR = user("9").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE,
            () -> Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ);
    static final RequestPostProcessor AUDITOR_READING = user("7").authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);
    static final RequestPostProcessor AUDITOR = user("8").authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);

    @Autowired
    AuditLog audit;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mvc;

    private void record(AuditEntry entry) {
        tx.executeWithoutResult(status -> audit.record(entry));
    }

    @BeforeEach
    void history() {
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_update_delete").update();
        jdbc.sql("alter table platform_audit.audit_entries disable trigger audit_entries_reject_truncate").update();
        jdbc.sql("delete from platform_audit.audit_entries").update();
        jdbc.sql("alter table platform_audit.audit_entries enable trigger audit_entries_reject_update_delete").update();
        jdbc.sql("alter table platform_audit.audit_entries enable trigger audit_entries_reject_truncate").update();
        UserId employee = new UserId(1001);
        UserId administrator = new UserId(9);
        record(new AuditEntry("platform.identity.login.succeeded", employee, null, "user-account:1001", "method=WECOM_WORKBENCH address=10.0.0.5 agent=x")
                .withTrace("10.0.0.5", AuditOutcome.SUCCEEDED, "WECOM_WORKBENCH"));
        record(new AuditEntry("platform.identity.login.failed", employee, null, "user-account:1001", "reason=secret-reason")
                .withTrace("10.0.0.6", AuditOutcome.FAILED, "WECOM_WORKBENCH"));
        record(new AuditEntry("platform.identity.logout", employee, null, "user-account:1001", "logout by the user")
                .withTrace("10.0.0.5", AuditOutcome.SUCCEEDED, "WECOM_WORKBENCH"));
        record(new AuditEntry("platform.identity.session.revoked", administrator, null, "user-account:1001", "forced logout: lost phone")
                .withTrace(null, AuditOutcome.SUCCEEDED, null));
        record(new AuditEntry("platform.identity.session.revoked", administrator, null, "user-account:1002", "forced logout: other person")
                .withTrace(null, AuditOutcome.SUCCEEDED, null));
        record(new AuditEntry("platform.identity.account.roles.replaced", administrator, null, "user-account:1001", "roles changed"));
        record(new AuditEntry("platform.identity.login.failed", null, null, "login:WECOM_WORKBENCH", "unknown person")
                .withTrace("10.0.0.7", AuditOutcome.FAILED, "WECOM_WORKBENCH"));
    }

    @Test
    @DisplayName("a person sees their own logins, logouts and ended sessions (forced ones included), newest first, and nothing else")
    void ownRecordsOnly() throws Exception {
        mvc.perform(get(OWN).with(EMPLOYEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].action").value("platform.identity.session.revoked"))
                .andExpect(jsonPath("$[1].action").value("platform.identity.logout"))
                .andExpect(jsonPath("$[2].action").value("platform.identity.login.failed"))
                .andExpect(jsonPath("$[2].outcome").value("FAILED"))
                .andExpect(jsonPath("$[2].sourceAddress").value("10.0.0.6"))
                .andExpect(jsonPath("$[3].action").value("platform.identity.login.succeeded"))
                .andExpect(jsonPath("$[3].loginMethod").value("WECOM_WORKBENCH"));
    }

    @Test
    @DisplayName("only the structured fields leave: no detail, no actor, no subject, so the free text and the administrator's identity stay out")
    void onlyTheWhitelistedFields() throws Exception {
        String body = mvc.perform(get(OWN).with(EMPLOYEE)).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("detail", "actorId", "subjectKey", "secret-reason", "lost phone", "forced logout").doesNotContain("\"9\"");
        mvc.perform(get(OWN).with(EMPLOYEE)).andExpect(jsonPath("$[0].keys()").exists());
    }

    @Test
    @DisplayName("the administrator who ended someone's sessions does not see that entry among their own, and nobody asks for another person")
    void theAdministratorDoesNotSeeWhatTheyDidToOthers() throws Exception {
        mvc.perform(get(OWN).with(ADMINISTRATOR)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(OWN).param("userId", "1001").with(ADMINISTRATOR)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("reading needs the own-login-record permission; the audit read permission does not give it, and a limit is checked")
    void permissionsAndLimit() throws Exception {
        mvc.perform(get(OWN)).andExpect(status().isUnauthorized());
        mvc.perform(get(OWN).with(AUDITOR_READING)).andExpect(status().isForbidden());
        mvc.perform(get(OWN).param("limit", "0").with(EMPLOYEE)).andExpect(status().isBadRequest());
        mvc.perform(get(OWN).param("limit", "201").with(EMPLOYEE)).andExpect(status().isBadRequest());
        mvc.perform(get(OWN).param("limit", "2").with(EMPLOYEE)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("the export needs its own permission, returns the matching entries as CSV, and is itself an audit entry")
    void exportIsAuditedAndNeedsItsPermission() throws Exception {
        mvc.perform(get(EXPORT)).andExpect(status().isUnauthorized());
        mvc.perform(get(EXPORT).with(AUDITOR_READING)).andExpect(status().isForbidden());
        mvc.perform(get(EXPORT).with(EMPLOYEE)).andExpect(status().isForbidden());

        var response = mvc.perform(get(EXPORT).param("action", "platform.identity.login.failed").with(AUDITOR)).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains("audit-entries.csv");
        String[] lines = response.getContentAsString().split("\r\n");
        assertThat(lines[0]).startsWith("entryId,createdAt,action,actorId");
        assertThat(lines).hasSize(3);
        assertThat(jdbc.sql("select actor_id || '|' || detail from platform_audit.audit_entries where action = 'platform.audit.audit-entry.exported'")
                .query(String.class).single()).startsWith("8|rows=2 filter=action=platform.identity.login.failed");
    }

    @Test
    @DisplayName("a cell that a spreadsheet would run as a formula is neutralised")
    void csvInjectionIsNeutralised() throws Exception {
        record(new AuditEntry("platform.test.formula", new UserId(8), null, "test:1", "=HYPERLINK(\"http://x\")"));

        String csv = mvc.perform(get(EXPORT).param("action", "platform.test.formula").with(AUDITOR)).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"");
    }

    @Test
    @DisplayName("B1-M9 a formula that starts with spaces or with a full-width trigger character is neutralised as well")
    void spacedAndFullWidthFormulasAreNeutralisedToo() throws Exception {
        record(new AuditEntry("platform.test.formula-wide", new UserId(8), null, "test:1", " =cmd"));
        record(new AuditEntry("platform.test.formula-wide", new UserId(8), null, "test:2", "\u3000+1"));
        record(new AuditEntry("platform.test.formula-wide", new UserId(8), null, "test:3", "\uFF1Dcmd"));
        record(new AuditEntry("platform.test.formula-wide", new UserId(8), null, "test:4", "plain text"));

        String csv = mvc.perform(get(EXPORT).param("action", "platform.test.formula-wide").with(AUDITOR)).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("\"' =cmd\"", "\"'\u3000+1\"", "\"'\uFF1Dcmd\"").contains(",plain text,").doesNotContain("'plain");
    }

    @Test
    @DisplayName("a filter matching more than the limit is refused instead of cut off, and writes no export entry")
    void tooManyRowsAreRefused() throws Exception {
        mvc.perform(get(EXPORT).with(AUDITOR))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_AUDIT_EXPORT_TOO_LARGE"));

        assertThat(jdbc.sql("select count(*) from platform_audit.audit_entries where action = 'platform.audit.audit-entry.exported'").query(Long.class).single()).isZero();
    }
}
