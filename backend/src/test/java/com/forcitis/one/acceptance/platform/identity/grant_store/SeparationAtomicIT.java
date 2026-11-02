package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A, DD sign-off section A and card done-when "Same transaction on separation: grants closed, audit entries with ids written back,
 * sessions revoked" (ADR-0055 decision 3 item 2, ADR-0034 decision 3 items 2 and 3). A part of the disposal is made to fail in the database;
 * the whole disposal must then leave no trace (no grant closed, none half-written, session alive), and once the fault is gone the redelivered
 * event completes it exactly once.
 */
class SeparationAtomicIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationAtomicIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    IncompleteEventPublications incomplete;

    private static final LocalDate HR_DATE = LocalDate.of(2026, 10, 20);

    private void admin(String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password()); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    @AfterEach
    void removeTheFaults() throws SQLException {
        admin("drop trigger if exists tg_gs_fail_audit on platform_audit.audit_entries", "drop trigger if exists tg_gs_fail_session on platform_identity.sessions",
                "drop function if exists platform_audit.gs_fail()");
    }

    private void assertUntouched(SignedIn leaver, List<Grant> before) throws Exception {
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertThat(grants(leaver.userId())).as("not one grant is closed or half-written").isEqualTo(before);
            assertThat(profileStatus(leaver)).isEqualTo(200);
        });
    }

    private void assertCompletesOnRedelivery(SignedIn leaver, List<Grant> before, UUID command) throws Exception {
        admin("drop trigger if exists tg_gs_fail_audit on platform_audit.audit_entries", "drop trigger if exists tg_gs_fail_session on platform_identity.sessions");
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            incomplete.resubmitIncompletePublications(publication -> true);
            assertThat(openGrants(leaver.userId())).isEmpty();
        });
        List<Grant> closed = grants(leaver.userId());
        assertThat(closed).hasSameSizeAs(before);
        assertThat(closed).allSatisfy(grant -> {
            assertThat(grant.cause()).isEqualTo("SEPARATION");
            assertThat(grant.separationCommandId()).isEqualTo(command);
            assertThat(grant.revocationAuditEntryId()).isNotNull();
        });
        assertThat(profileStatus(leaver)).isEqualTo(401);
    }

    @Test
    @DisplayName("同一事务 ADR-0034 第 3 项第 3 条：role.revoked 审计写入失败，则授予一条都不关、会话仍在；故障排除后重投的事件把全部关完，每行恰一条审计")
    void anAuditFailureLeavesTheGrantsOpen() throws Exception {
        SignedIn leaver = person("business_admin", "org_data_admin");
        List<Grant> before = grants(leaver.userId());
        long entries = auditCount(REVOKED_ACTION);
        admin("""
                create function platform_audit.gs_fail() returns trigger language plpgsql as $$
                begin raise exception 'acceptance fault' using errcode = 'P0001'; end $$""",
                "create trigger tg_gs_fail_audit before insert on platform_audit.audit_entries for each row when (new.action = '" + REVOKED_ACTION
                        + "') execute function platform_audit.gs_fail()");
        UUID command = newCommandId();

        publishSeparated(leaver.employeeId(), command, HR_DATE);

        assertUntouched(leaver, before);
        assertThat(auditCount(REVOKED_ACTION)).isEqualTo(entries);
        assertCompletesOnRedelivery(leaver, before, command);
        assertThat(auditCount(REVOKED_ACTION) - entries).as("one entry per grant, none doubled by the failed attempt").isEqualTo(before.size());
    }

    @Test
    @DisplayName("同一事务 ADR-0055 第 3 项第 2 条：会话吊销失败，则授予一条都不关；故障排除后重投的事件完成处置")
    void aSessionRevokeFailureLeavesTheGrantsOpen() throws Exception {
        SignedIn leaver = person("business_admin");
        List<Grant> before = grants(leaver.userId());
        admin("""
                create function platform_audit.gs_fail() returns trigger language plpgsql as $$
                begin raise exception 'acceptance fault' using errcode = 'P0001'; end $$""",
                "create trigger tg_gs_fail_session before delete on platform_identity.sessions for each row execute function platform_audit.gs_fail()");
        UUID command = newCommandId();

        publishSeparated(leaver.employeeId(), command, HR_DATE);

        assertUntouched(leaver, before);
        assertCompletesOnRedelivery(leaver, before, command);
    }
}
