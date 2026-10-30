package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part A, old card 0034-G4-01 / G4-03 re-read for EmployeeSeparated (ADR-0055 decision 3 item 2 and 5, ADR-0034 decision 3 items 1 to 4,
 * ADR-0068 decision 1 item 9 and decision 2 item 5, DD sign-off of #732 section A). The event is the one organization.workforce publishes; its
 * personnel command id lands in revocation_separation_command_id, the close time is the injected clock and not the HR date.
 */
class SeparationClosesGrantsIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationClosesGrantsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A throw-away encryption key for the one-time-code secrets of the emergency accounts, generated per run. */
    @DynamicPropertySource
    static void totpKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String encoded = java.util.Base64.getEncoder().encodeToString(key);
        registry.add("forcitis.platform.identity.emergency.totp-encryption-key", () -> encoded);
    }

    /** An HR date well before the clock: a close that took it would be visible. */
    static final LocalDate HR_DATE = LocalDate.of(2026, 10, 20);
    private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})");

    private void awaitNoOpenGrants(UUID user) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(openGrants(user)).isEmpty());
    }

    private static List<Instant> instantsIn(String text) {
        Matcher matcher = ISO.matcher(text);
        List<Instant> found = new java.util.ArrayList<>();
        while (matcher.find()) {
            found.add(OffsetDateTime.parse(matcher.group()).toInstant());
        }
        return found;
    }

    @Test
    @DisplayName("0034-G4-01 ADR-0055 第 3 项第 2 条、ADR-0034 第 3 项第 1、3 条：EmployeeSeparated 到达，此人含 super_admin 与 security_auditor 的全部开放授予关闭，授予行总数不变；每行原因 SEPARATION、离职命令 ID 等于事件的命令 ID、收回人为空、关闭时刻等于注入时钟而不是人事日期；每关一行一条 role.revoked 审计，条目 ID 回写该行，detail 含授予行 ID、原因与实际钟点")
    void everyOpenGrantIsClosedWithTheSeparationFacts() throws Exception {
        SignedIn leaver = person("super_admin", "security_auditor", "business_admin");
        SignedIn other = person("business_admin");
        List<Grant> before = grants(leaver.userId());
        assertThat(before).extracting(Grant::roleCode).contains("employee", "super_admin", "security_auditor", "business_admin");
        assertThat(before).allMatch(Grant::open);
        long total = grantRows();
        long revokedEntries = auditCount(REVOKED_ACTION);
        UUID command = newCommandId();

        publishSeparated(leaver.employeeId(), command, HR_DATE);

        awaitNoOpenGrants(leaver.userId());
        List<Grant> after = grants(leaver.userId());
        assertThat(grantRows()).as("closed, never deleted: the grant total is unchanged").isEqualTo(total);
        assertThat(after).extracting(Grant::id).containsExactlyInAnyOrderElementsOf(before.stream().map(Grant::id).toList());
        assertThat(after).allSatisfy(grant -> {
            assertThat(grant.cause()).isEqualTo("SEPARATION");
            assertThat(grant.separationCommandId()).as("the command of the event, not a sync run").isEqualTo(command);
            assertThat(grant.revokedBy()).as("the system closes, nobody revokes").isNull();
            assertThat(grant.to()).as("the actual clock, not the HR date").isEqualTo(NOW);
            assertThat(grant.revocationAuditEntryId()).isNotNull();
            AuditRow entry = audit(grant.revocationAuditEntryId());
            assertThat(entry.action()).isEqualTo(REVOKED_ACTION);
            assertThat(entry.detail()).contains(grant.id().toString()).containsIgnoringCase("separation");
            assertThat(instantsIn(entry.detail())).as(entry.detail()).contains(NOW);
        });
        assertThat(after.stream().map(Grant::revocationAuditEntryId).distinct().count()).as("one entry per closed grant").isEqualTo(after.size());
        assertThat(auditCount(REVOKED_ACTION) - revokedEntries).isEqualTo(after.size());
        assertThat(openGrants(other.userId())).as("nobody else is touched").isNotEmpty();
        assertThat(profileStatus(other)).isEqualTo(200);
    }

    @Test
    @DisplayName("ADR-0055 第 3 项第 2 条、ADR-0034 第 3 项第 2 条：同一处置里会话即时吊销、应急账号停用、浏览器标识收回；别人的不受影响")
    void sessionsEmergencyAccountAndBrowserIdentifiersGoWithTheGrants() throws Exception {
        SignedIn leaverEarlier = person("super_admin");
        SignedIn otherEarlier = person("super_admin");
        assertThat(operation("--operation=register-emergency-admin", "--employee-id=" + text(leaverEarlier.employeeId()), "--login-name=gsbreak" + serialOf(leaverEarlier.employeeId()))).isZero();
        assertThat(operation("--operation=register-emergency-admin", "--employee-id=" + text(otherEarlier.employeeId()), "--login-name=gsother" + serialOf(otherEarlier.employeeId()))).isZero();
        SignedIn first = signIn(leaverEarlier.employeeId());
        assertThat(mvc.perform(post("/api/v1/platform/sessions:confirm-identity").with(csrf()).with(session(first))).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(liveBrowserIdentifiers(first.userId())).isPositive();
        SignedIn leaver = signIn(leaverEarlier.employeeId());
        SignedIn other = signIn(otherEarlier.employeeId());
        assertThat(profileStatus(leaver)).isEqualTo(200);

        publishSeparated(leaver.employeeId(), newCommandId(), HR_DATE);

        awaitNoOpenGrants(leaver.userId());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(emergencyAdminEnabled(leaver.userId())).isFalse());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(liveBrowserIdentifiers(leaver.userId())).isZero());
        assertThat(sessionRows(leaver.userId())).isZero();
        assertThat(profileStatus(leaver)).isEqualTo(401);
        assertThat(emergencyAdminEnabled(other.userId())).isTrue();
        assertThat(profileStatus(other)).isEqualTo(200);
    }

    @Test
    @DisplayName("0034-G4-01/G4-03 等价 ADR-0034 第 3 项第 1 条：同一事件投递两次（同 eventId）以及同一命令的另一事件，第二次什么都不改：授予行、关闭列、审计条数、授予总数都不变")
    void aReplayChangesNothing() throws Exception {
        SignedIn leaver = person("business_admin");
        UUID command = newCommandId();
        var first = separated(leaver.employeeId(), command, HR_DATE);
        publish(first);
        awaitNoOpenGrants(leaver.userId());
        List<Grant> closed = grants(leaver.userId());
        long total = grantRows();
        long entries = auditCount(REVOKED_ACTION);
        clock.set(NOW.plus(Duration.ofHours(1)));

        publish(first);
        publishSeparated(leaver.employeeId(), command, HR_DATE);
        org.awaitility.Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(grants(leaver.userId())).isEqualTo(closed);
            assertThat(grantRows()).isEqualTo(total);
            assertThat(auditCount(REVOKED_ACTION)).isEqualTo(entries);
        });
    }

    @Test
    @DisplayName("ADR-0034 第 3 项第 1、4 条：事件只关闭此人当时开放的授予；已关闭的旧行不动；事件到达前已手工收回的角色不被改写关闭列")
    void alreadyClosedGrantsAreLeftAlone() throws Exception {
        SignedIn administrator = administrator();
        SignedIn leaver = person();
        String role = customRole(administrator, "SELF", PLAIN_CODES.get(0));
        replacedRoles(administrator, leaver.userId(), List.of("employee", role));
        replacedRoles(administrator, leaver.userId(), List.of("employee"));
        Grant revoked = grants(leaver.userId(), role).getFirst();
        assertThat(revoked.cause()).isEqualTo("MANUAL");

        publishSeparated(leaver.employeeId(), newCommandId(), HR_DATE);

        awaitNoOpenGrants(leaver.userId());
        assertThat(grants(leaver.userId(), role)).containsExactly(revoked);
        assertThat(grants(leaver.userId(), "employee").getFirst().cause()).isEqualTo("SEPARATION");
    }

    @Test
    @DisplayName("空范围 ADR-0034 第 3 项第 1 条：员工没有账号，EmployeeSeparated 不报错、不建账号、不写授予、不告警")
    void aPersonWithoutAnAccountIsHarmless() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID employee = newEmployeeId();
        employee(employee, unit, null);
        long accounts = accountRows();
        long total = grantRows();
        int mark = logMark();

        publishSeparated(employee, newCommandId(), HR_DATE);

        org.awaitility.Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(accountRows()).isEqualTo(accounts);
            assertThat(grantRows()).isEqualTo(total);
        });
        assertThat(alertsSince(mark)).isEmpty();
    }

    @Test
    @DisplayName("ADR-0034 第 3 项第 1、4 条（不得做：只认本人的 EmployeeSeparated）：另一名员工的 EmployeeSeparated 到达，此人的授予行、会话、应急账号原样不动")
    void anotherEmployeesEventLeavesThePersonAlone() throws Exception {
        SignedIn stays = person("super_admin", "business_admin");
        SignedIn leaves = person("business_admin");
        List<Grant> before = grants(stays.userId());

        publishSeparated(leaves.employeeId(), newCommandId(), HR_DATE);

        awaitNoOpenGrants(leaves.userId());
        assertThat(grants(stays.userId())).isEqualTo(before);
        assertThat(profileStatus(stays)).isEqualTo(200);
    }

    /** Writes a separation command of the person and its hold straight into the workforce schema, as the held / released-pending states leave them; no event is published. */
    private void writeSeparation(SignedIn person, String commandStatus, String holdStatus, LocalDate effectiveDate) throws Exception {
        java.time.OffsetDateTime at = java.time.OffsetDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC);
        UUID command = newCommandId();
        try (java.sql.Connection workforce = schemaConnection("organization_workforce")) {
            try (java.sql.PreparedStatement insert = workforce.prepareStatement("""
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                    values (?, ?, 'SEPARATE', ?, ?, ?, ?, ?)""")) {
                insert.setObject(1, command);
                insert.setObject(2, stored(person.employeeId()));
                insert.setObject(3, effectiveDate);
                insert.setString(4, commandStatus);
                insert.setObject(5, at);
                insert.setObject(6, person.userId());
                insert.setObject(7, at);
                insert.executeUpdate();
            }
            boolean released = "RELEASED".equals(holdStatus);
            try (java.sql.PreparedStatement insert = workforce.prepareStatement("""
                    insert into separation_holds (separation_hold_id, personnel_command_id, employee_id, status, released_at, released_by, release_reason,
                           release_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
                insert.setObject(1, newCommandId());
                insert.setObject(2, command);
                insert.setObject(3, stored(person.employeeId()));
                insert.setString(4, holdStatus);
                insert.setObject(5, released ? at : null);
                insert.setObject(6, released ? person.userId() : null);
                insert.setString(7, released ? "synthetic release" : null);
                insert.setString(8, released ? "INTERFACE" : null);
                insert.setObject(9, at);
                insert.setObject(10, at);
                insert.executeUpdate();
            }
        }
    }

    private void assertNothingClosesWhile(SignedIn person, List<Grant> before) {
        org.awaitility.Awaitility.await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertThat(grants(person.userId())).isEqualTo(before);
            assertThat(profileStatus(person)).isEqualTo(200);
        });
    }

    @Test
    @DisplayName("0034-G4-02 等价 ADR-0068 第 2 条第 2 项、ADR-0055 第 3 项第 3 条：此人有一条待放行（HELD）的离职命令、没有 EmployeeSeparated：授予不关闭、会话仍在")
    void aHeldSeparationClosesNothing() throws Exception {
        SignedIn held = person("super_admin", "business_admin");
        List<Grant> before = grants(held.userId());

        writeSeparation(held, "HELD", "HELD", TODAY.plusDays(10));

        assertNothingClosesWhile(held, before);
    }

    @Test
    @DisplayName("0034-G4-02 等价 ADR-0068 第 15 条第 3 项与 #732 裁定：已放行但生效日在将来（命令 PENDING、放行记录 RELEASED）、尚无 EmployeeSeparated：授予不关闭、会话仍在；事件到达后才关闭")
    void aReleasedSeparationWaitingForItsDateClosesNothingUntilTheEvent() throws Exception {
        SignedIn waiting = person("super_admin", "business_admin");
        List<Grant> before = grants(waiting.userId());

        writeSeparation(waiting, "PENDING", "RELEASED", TODAY.plusDays(10));

        assertNothingClosesWhile(waiting, before);
        publishSeparated(waiting.employeeId(), newCommandId(), TODAY.plusDays(10));
        awaitNoOpenGrants(waiting.userId());
    }

    @Test
    @DisplayName("ADR-0055 第 3 项第 4 条、ADR-0068 第 2 条第 3 项：唯一可用超管的离职由人事侧判定与放行；EmployeeSeparated 到达时 identity 照关他的全部授予（含 super_admin），身份侧不设下限拒绝")
    void theLastAvailableSuperAdminIsClosedWithoutAnIdentityRefusal() throws Exception {
        SignedIn last = person("super_admin");
        for (Grant other : grantsOfOtherSuperAdmins(last.userId())) {
            closeGrant(other.id(), other.from().isAfter(NOW) ? other.from() : NOW);
        }
        assertThat(openGrants(last.userId())).extracting(Grant::roleCode).contains("super_admin");

        publishSeparated(last.employeeId(), newCommandId(), HR_DATE);

        awaitNoOpenGrants(last.userId());
        assertThat(grants(last.userId())).allMatch(g -> "SEPARATION".equals(g.cause()));
    }

    private List<Grant> grantsOfOtherSuperAdmins(UUID except) throws java.sql.SQLException {
        List<Grant> found = new java.util.ArrayList<>();
        try (java.sql.Connection identity = schemaConnection("platform_identity"); java.sql.PreparedStatement select = identity.prepareStatement(
                "select distinct g.user_id from account_role_grants g join roles r on r.role_id = g.role_id where r.code = 'super_admin' and g.effective_to_at is null and g.user_id <> ?")) {
            select.setObject(1, except);
            try (java.sql.ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    found.addAll(grants(rs.getObject(1, UUID.class), "super_admin").stream().filter(Grant::open).toList());
                }
            }
        }
        return found;
    }
}
