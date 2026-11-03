package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #939, old card 0034-G4-03' (ADR-0034 decision 3 item 5, ADR-0055 decision 3 item 6, ADR-0068 decision 1 item 10, A-13, C-2, D-1a-17):
 * platform.identity consumes EmployeeReinstated. After checking that the directory shows the person employed and that the account has
 * no open grant, it opens exactly one grant of the employee role through the DEFAULT_ROLE channel, with nobody as granter (D-5) and a
 * written audit entry. The grants closed by the separation stay closed. A redelivered or out-of-order event changes nothing. The
 * separation and the reinstatement are entered as personnel commands; the grant rows are read from platform_identity.account_role_grants
 * by the columns signed off for #732.
 */
class ReinstatementOpensBaseRoleIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(ReinstatementOpensBaseRoleIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 10, 20);
    static final LocalDate BACK_ON = LocalDate.of(2026, 11, 1);
    static final Duration SETTLE = Duration.ofSeconds(20);

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private com.forcitis.one.platform.identity.application.OperationsCommands identityOperations;

    record Grant(UUID id, String roleCode, Instant from, Instant to, String channel, UUID grantedBy, UUID grantAuditEntryId) {
        boolean open() {
            return to == null;
        }
    }

    /** An employed person whose account holds employee and business_admin; answers the employee id. */
    private UUID employedWithRoles() throws Exception {
        UUID employee = employed(REGULAR);
        grantByOperations(employee, "business_admin");
        return employee;
    }

    private UUID accountOf(UUID employee) throws SQLException {
        List<UUID> accounts = accountsOfEmployee(employee);
        assertThat(accounts).as("one account for the employee").hasSize(1);
        return accounts.getFirst();
    }

    private List<Grant> grants(UUID user) throws SQLException {
        List<Grant> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select g.account_role_grant_id, r.code, g.effective_from_at, g.effective_to_at, g.grant_channel_kind, g.granted_by, g.grant_audit_entry_id
                  from account_role_grants g join roles r on r.role_id = g.role_id where g.user_id = ?
                 order by g.effective_from_at, g.account_role_grant_id""")) {
            select.setObject(1, user);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    OffsetDateTime to = rs.getObject(4, OffsetDateTime.class);
                    rows.add(new Grant(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant(),
                            to == null ? null : to.toInstant(), rs.getString(5), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class)));
                }
            }
        }
        return rows;
    }

    private List<Grant> openGrants(UUID user) throws SQLException {
        return grants(user).stream().filter(Grant::open).toList();
    }

    private String auditActionOf(UUID entryId) throws SQLException {
        try (Connection audit = schemaConnection("platform_audit");
             PreparedStatement select = audit.prepareStatement("select action from audit_entries where id = ?")) {
            select.setObject(1, entryId);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).as("the audit entry %s exists", entryId).isTrue();
                return rs.getString(1);
            }
        }
    }

    private long auditEntries() throws SQLException {
        try (Connection audit = schemaConnection("platform_audit");
             PreparedStatement select = audit.prepareStatement("select count(*) from audit_entries where action like 'platform.identity.%'");
             ResultSet rs = select.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Separates the person through a personnel command dated in the past and waits until identity closed every grant. */
    private void separatedAndClosed(UUID employee, UUID user) throws SQLException {
        assertThat(openGrants(user)).as("premise (#732 grant store): the employed account holds open grants").extracting(Grant::roleCode).contains("employee");
        separate(employee, LAST_WORKING_DAY);
        Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(openGrants(user)).isEmpty());
    }

    private UUID reinstated(UUID employee) throws Exception {
        UUID command = reinstate(employee, BACK_ON, newUnit(), newPosition());
        assertThat(command(command).get("status")).as("premise: the reinstatement took effect").isEqualTo(EFFECTIVE);
        return command;
    }

    /**
     * The event as organization.workforce publishes it (docs/architecture/events/workforce.yaml EmployeeReinstated): businessDate is the
     * effective date, legalEntityId the person's legal entity on that day, occurredAt after the separation took effect.
     */
    private EmployeeReinstated reinstatedEvent(UUID employee, UUID command) {
        EventEnvelope envelope = new EventEnvelope(Uuids.timeOrdered(NOW), "organization.workforce.EmployeeReinstated", 1, NOW, "Employee", employee, 1,
                Uuids.timeOrdered(NOW), null, ActorType.SYSTEM, com.forcitis.one.kernel.SystemActors.idOf("test.envelope"), new LegalEntityId(legalEntity),
                BACK_ON, null, List.of());
        return new EmployeeReinstated(envelope, new PersonnelCommandId(command), employeeIdOf(employee), BACK_ON);
    }

    private void publish(Object event) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(event));
    }

    /**
     * Waits until platform.identity's listener has finished the event: at least one identity publication of it exists and none of its
     * publications is still open. A listener that throws, or never runs, leaves this waiting and the case red; the failure lists the
     * publications of the event that are still open, so a timeout names its cause.
     */
    private void identityHandled(EmployeeReinstated event) throws SQLException {
        String id = event.envelope().eventId().toString();
        try {
            Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).until(() -> {
                List<Map<String, Object>> publications = publicationsOf(id);
                long identity = publications.stream().filter(row -> String.valueOf(row.get("listener_id")).contains(".platform.identity.")).count();
                return identity > 0 && publications.stream().noneMatch(row -> row.get("completion_date") == null);
            });
        } catch (ConditionTimeoutException timeout) {
            throw new AssertionError("identity did not finish event " + id + " within " + SETTLE + "; publications of the event (open ones have no completion): "
                    + describeOpen(publicationsOf(id)), timeout);
        }
    }

    private List<Map<String, Object>> publicationsOf(String eventId) throws SQLException {
        List<Map<String, Object>> found = new ArrayList<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                found.addAll(query(registry, "select listener_id, status, completion_attempts, completion_date from " + table + " where serialized_event like ?",
                        "%" + eventId + "%"));
            }
        }
        return found;
    }

    private static String describeOpen(List<Map<String, Object>> publications) {
        List<String> open = publications.stream().filter(row -> row.get("completion_date") == null)
                .map(row -> row.get("listener_id") + " status=" + row.get("status") + " attempts=" + row.get("completion_attempts")).toList();
        return open.isEmpty() ? "none open, " + publications.size() + " in all and no identity listener among them" : String.join("; ", open);
    }

    private void revokeByOperations(UUID employee, String roleCode) {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int exit = identityOperations.run(new org.springframework.boot.DefaultApplicationArguments("--operation=revoke-role", "--employee-id=" + text(employee),
                "--role-code=" + roleCode, "--operator=01900000-0000-7000-8000-00000000a001", "--witness=01900000-0000-7000-8000-00000000a002"),
                new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8),
                new java.io.PrintStream(err, true, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(exit).as("revoke-role " + roleCode + ": " + err.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
    }

    private void nothingChanged(UUID user, List<Grant> grants, long entries) throws SQLException {
        assertThat(grants(user)).isEqualTo(grants);
        assertThat(auditEntries()).isEqualTo(entries);
    }

    @Test
    @DisplayName("#939 0034-G4-03' ADR-0034 第 3 项第 5 条、D-1a-17：离职（关闭全部授予）后经人事命令复职，只新开一条 employee 授予，渠道 DEFAULT_ROLE，授予人为空，回写的审计条目存在；离职前的 employee、business_admin 授予仍关闭、行不变")
    void aReinstatementOpensOnlyTheEmployeeRole() throws Exception {
        UUID employee = employedWithRoles();
        UUID user = accountOf(employee);
        assertThat(grants(user)).extracting(Grant::roleCode).contains("employee", "business_admin");
        separatedAndClosed(employee, user);
        List<Grant> closed = grants(user);

        reinstated(employee);

        List<Grant> open = Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).until(() -> openGrants(user), found -> !found.isEmpty());
        assertThat(open).as("exactly one open grant").hasSize(1);
        Grant base = open.getFirst();
        assertThat(base.roleCode()).isEqualTo("employee");
        assertThat(base.channel()).isEqualTo("DEFAULT_ROLE");
        assertThat(base.grantedBy()).as("D-5: only MANUAL grants name a granter").isNull();
        assertThat(base.grantAuditEntryId()).as("the grant is audited").isNotNull();
        assertThat(auditActionOf(base.grantAuditEntryId())).startsWith("platform.identity.");
        assertThat(closed).as("a new row, not a reopened one").extracting(Grant::id).doesNotContain(base.id());
        assertThat(grants(user)).as("the grants closed by the separation stay as they were").containsAll(closed).hasSize(closed.size() + 1);
    }

    @Test
    @DisplayName("#939 0034-G4-03' A-13 不靠授予历史推断：离职前已用 revoke-role 收回全部角色（离职时没有开放授予可关），复职后仍只开一条 DEFAULT_ROLE 的 employee 授予")
    void aReinstatementOpensTheEmployeeRoleAlsoWhenNothingWasOpenAtSeparation() throws Exception {
        UUID employee = employed(REGULAR);
        grantByOperations(employee, "business_admin");
        UUID user = accountOf(employee);
        revokeByOperations(employee, "business_admin");
        revokeByOperations(employee, "employee");
        assertThat(openGrants(user)).as("premise: no open grant before the separation").isEmpty();
        assertThat(grants(user)).as("premise (#732 grant store): the revoked grants are kept as closed rows").isNotEmpty();
        separate(employee, LAST_WORKING_DAY);

        reinstated(employee);

        List<Grant> open = Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).until(() -> openGrants(user), found -> !found.isEmpty());
        assertThat(open).hasSize(1).first().satisfies(base -> {
            assertThat(base.roleCode()).isEqualTo("employee");
            assertThat(base.channel()).isEqualTo("DEFAULT_ROLE");
        });
    }

    @Test
    @DisplayName("#939 0034-G4-03' 幂等：复职已开过 employee 授予后，同一 EmployeeReinstated 再投递两次（同 eventId），以及同一命令的另一事件，都不再开授予、不写审计")
    void aRedeliveryChangesNothing() throws Exception {
        UUID employee = employedWithRoles();
        UUID user = accountOf(employee);
        separatedAndClosed(employee, user);
        UUID command = reinstated(employee);
        Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(openGrants(user)).hasSize(1));
        List<Grant> after = grants(user);
        long entries = auditEntries();

        EmployeeReinstated again = reinstatedEvent(employee, command);
        EmployeeReinstated another = reinstatedEvent(employee, command);
        publish(again);
        publish(again);
        publish(another);
        identityHandled(again);
        identityHandled(another);

        nothingChanged(user, after, entries);
    }

    @Test
    @DisplayName("#939 0034-G4-03' A-13 先核对目录：此人离职后目录仍为离职，却到了一个 EmployeeReinstated（乱序或伪造），不开授予、不写审计")
    void anEventWhileTheDirectoryShowsSeparatedOpensNothing() throws Exception {
        UUID employee = employedWithRoles();
        UUID user = accountOf(employee);
        separatedAndClosed(employee, user);
        List<Grant> closed = grants(user);
        long entries = auditEntries();

        EmployeeReinstated event = reinstatedEvent(employee, newPersonnelCommandId());
        publish(event);
        identityHandled(event);

        nothingChanged(user, closed, entries);
    }

    @Test
    @DisplayName("#939 0034-G4-03' 账号已有开放授予：在职员工（从未离职）收到 EmployeeReinstated，不再开 employee 授予，其现有授予不变")
    void anAccountWithAnOpenGrantGetsNothingMore() throws Exception {
        UUID employee = employedWithRoles();
        UUID user = accountOf(employee);
        List<Grant> before = grants(user);
        assertThat(openGrants(user)).as("premise (#732 grant store): the account holds open grants").extracting(Grant::roleCode).contains("employee", "business_admin");
        long entries = auditEntries();

        EmployeeReinstated event = reinstatedEvent(employee, newPersonnelCommandId());
        publish(event);
        identityHandled(event);

        nothingChanged(user, before, entries);
    }

    @Test
    @DisplayName("#939 空范围：没有账号的员工离职后复职，不建账号、不写授予")
    void aPersonWithoutAnAccountGetsNoAccount() throws Exception {
        UUID employee = employed(REGULAR);
        assertThat(accountsOfEmployee(employee)).isEmpty();
        separate(employee, LAST_WORKING_DAY);

        reinstated(employee);

        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(accountsOfEmployee(employee)).isEmpty());
    }

    @Test
    @DisplayName("#939 只认本人：另一名员工复职，此人（离职、授予全关）的授予行不变")
    void anotherEmployeesReinstatementLeavesThePersonAlone() throws Exception {
        UUID stays = employedWithRoles();
        UUID staysUser = accountOf(stays);
        separatedAndClosed(stays, staysUser);
        List<Grant> closed = grants(staysUser);
        UUID other = employedWithRoles();
        UUID otherUser = accountOf(other);
        separatedAndClosed(other, otherUser);

        reinstated(other);

        Awaitility.await().atMost(SETTLE).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(openGrants(otherUser)).hasSize(1));
        assertThat(grants(staysUser)).isEqualTo(closed);
    }

    private static UUID newPersonnelCommandId() {
        return Uuids.timeOrdered(NOW);
    }
}
