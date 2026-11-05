package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.SeparationHeld;
import com.forcitis.one.organization.workforce.events.SeparationHoldReleased;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import com.forcitis.one.platform.identity.events.EmergencyAdminDisabled;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #932 acceptance: identity carries out the separation effects of ADR-0055 decision 3 item 2 when the local personnel command takes effect,
 * that is when workforce publishes EmployeeSeparated (docs/architecture/events/workforce.yaml; ADR-0068 item 2.5 C), and not before.
 * The grant store is account_role_grants: the subscriber closes the open rows and the rows stay.
 * Not asserted: the cache eviction of 3.2 (not observable once every session is revoked) and the revocation_separation_command_id
 * column of 3.5 (it lives in account_role_grants, #732).
 */
@DisplayName("#932 EmployeeSeparated closes the access of the separated person (ADR-0055 3.2, ADR-0068 2.5)")
class EmployeeSeparatedClosesAccessIntegrationTest extends IdentityIntegrationTest {

    private static final Duration DELIVERY = Duration.ofSeconds(15);
    private static final String ROLE_REVOKED = "platform.identity.account.role.revoked";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private static EmployeeSeparated separated(long id, LocalDate effectiveDate) {
        EmployeeId employee = TestAccounts.employee(id);
        return new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", employee.value()),
                new PersonnelCommandId(UUID.randomUUID()), employee, effectiveDate, new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
    }

    private void deliver(EmployeeSeparated event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    /** Deliveries of this very EmployeeSeparated to identity listeners, not of the events it causes: the publication registry is the only outside view of "handled". */
    private long identityCompletions(EmployeeSeparated event) {
        return jdbc.sql("""
                select count(*) from (select listener_id, event_type, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, event_type, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like 'com.forcitis.one.platform.identity.%' and p.event_type = :type and p.completion_date is not null
                   and p.serialized_event like :event
                """).param("type", EmployeeSeparated.class.getName()).param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single();
    }

    /**
     * Waits until every identity listener has finished the event. Two of them take EmployeeSeparated (the emergency-admin one and the one closing grants and sessions) and the
     * first can finish long before the second commits, so "any completion" is not "handled"; the registry row is written at commit, so completions > 0 with nothing
     * incomplete means all of them are done.
     */
    private void identityHandled(EmployeeSeparated event) {
        await().atMost(DELIVERY).untilAsserted(() -> {
            assertThat(identityCompletions(event)).as("identity listeners that completed the event").isPositive();
            assertThat(identityIncomplete(event)).as("identity listeners still working on the event").isZero();
        });
    }

    private long identityIncomplete(EmployeeSeparated event) {
        return jdbc.sql("""
                select count(*) from platform_integration.event_publication p
                 where p.listener_id like 'com.forcitis.one.platform.identity.%' and p.event_type = :type and p.completion_date is null
                   and p.serialized_event like :event
                """).param("type", EmployeeSeparated.class.getName()).param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single();
    }

    /** Publications of this event not yet completed by any listener; the registry row is written at commit, so 0 after a delivery means every listener is done. */
    private long incomplete(DomainEvent event) {
        return jdbc.sql("select count(*) from platform_integration.event_publication p where p.completion_date is null and p.serialized_event like :event")
                .param("event", "%" + event.envelope().eventId() + "%").query(Long.class).single();
    }

    private long roles(long id) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = :id)")
                .param("id", TestAccounts.employee(id).value()).query(Long.class).single();
    }

    private long holdersOf(String code) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id where ar.effective_to_at is null and lower(r.code) = :code")
                .param("code", code).query(Long.class).single();
    }

    private long sessions(long id) {
        return jdbc.sql("select count(*) from platform_identity.sessions where principal_name = (select ua.user_id::text from platform_identity.user_accounts ua where ua.employee_id = :id)")
                .param("id", TestAccounts.employee(id).value()).query(Long.class).single();
    }

    private boolean emergencyEnabled(long id) {
        return jdbc.sql("select is_enabled from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :id)")
                .param("id", TestAccounts.employee(id).value()).query(Boolean.class).single();
    }

    private void registerEmergencyAdmin(long id, String name) {
        emergencyAdmins.insertIfAbsent(EmergencyAdmin.register(new UserId(accountOf(id)), name, hasher.hash(UUID.randomUUID().toString()), vault.seal(com.forcitis.one.platform.identity.domain.Totp.newSecret())));
    }

    /** A confirmed browser identifier of the account; only its hash is stored, so a fixed row stands in for a confirmed login. */
    private void browserIdentifier(long id) {
        jdbc.sql("""
                insert into platform_identity.browser_identifiers (browser_identifier_id, user_id, identifier_hash, last_login_at)
                select uuidv7(), ua.user_id, md5(random()::text) || md5(random()::text), now() from platform_identity.user_accounts ua where ua.employee_id = :id
                """).param("id", TestAccounts.employee(id).value()).update();
    }

    private long browserIdentifiers(long id) {
        return jdbc.sql("select count(*) from platform_identity.browser_identifiers where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :id)")
                .param("id", TestAccounts.employee(id).value()).query(Long.class).single();
    }

    private int status(String session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("#932 EmployeeSeparated closes every grant of the person, super_admin and security_auditor included, one audit entry each with the separation cause; revokes the sessions, disables the emergency account, revokes the browser identifiers; nobody else is touched")
    void separationClosesEverything() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);
        createAccount(1001, "super_admin", "security_auditor", "org_data_admin");
        createAccount(1002, "super_admin");
        registerEmergencyAdmin(1001, "breakglass-a");
        registerEmergencyAdmin(1002, "breakglass-b");
        browserIdentifier(1001);
        browserIdentifier(1002);
        accessCache.evictAll();
        assertThat(roles(1001)).as("fixture: employee, super_admin, security_auditor, org_data_admin").isEqualTo(4);

        long grantRows = count("account_role_grants");
        deliver(separated(1001, LocalDate.of(2026, 10, 15)));

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1001)).isZero());
        assertThat(count("account_role_grants")).as("closed, never deleted: the grant total is unchanged").isEqualTo(grantRows);
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(browserIdentifiers(1001)).isZero());
        assertThat(recorded()).filteredOn(e -> e.action().equals(ROLE_REVOKED)).extracting(e -> e.detail().replaceAll("^grant=\\S+ (role=\\S+ cause=SEPARATION) at=\\S+$", "$1")).containsExactlyInAnyOrder(
                "role=employee cause=SEPARATION", "role=super_admin cause=SEPARATION", "role=security_auditor cause=SEPARATION",
                "role=org_data_admin cause=SEPARATION");
        assertThat(recorded()).filteredOn(e -> e.action().equals(ROLE_REVOKED))
                .allSatisfy(e -> assertThat(e.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(1001))));
        assertThat(recorded()).filteredOn(e -> e.action().equals("platform.identity.session.revoked"))
                .as("the session revocation names the separated account").isNotEmpty()
                .allSatisfy(e -> assertThat(e.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(1001))));
        assertThat(sessions(1001)).isZero();
        assertThat(status(victim)).as("the separated person's session no longer works").isEqualTo(401);
        assertThat(emergencyEnabled(1001)).isFalse();
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new UserId(accountOf(1001)));
            assertThat(e.cause()).isEqualTo(EmergencyAdminDisableCause.SEPARATION);
        });
        assertThat(domainEvents.of(AccountRolesReplaced.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new UserId(accountOf(1001)));
            assertThat(e.roleIds()).isEmpty();
        });
        assertThat(roles(1002)).as("nobody else is touched").isEqualTo(2);
        assertThat(sessions(1002)).isEqualTo(1);
        assertThat(status(other)).isEqualTo(200);
        assertThat(emergencyEnabled(1002)).isTrue();
        assertThat(browserIdentifiers(1002)).isEqualTo(1);
    }

    @Test
    @DisplayName("#932 the last super_admin is closed too: the floor was judged by workforce before the event (ADR-0068 2.2), identity does not refuse")
    void lastSuperAdminIsClosed() {
        createAccount(1003, "super_admin");
        assertThat(holdersOf("super_admin")).isEqualTo(1);

        EmployeeSeparated event = separated(1003, LocalDate.of(2026, 10, 15));
        deliver(event);

        identityHandled(event);
        assertThat(holdersOf("super_admin")).isZero();
        assertThat(roles(1003)).isZero();
        assertThat(identityIncomplete(event)).as("no identity listener left the event failed").isZero();
    }

    @Test
    @DisplayName("#932 a separated person whose account holds no role still loses the sessions, the browser identifiers and the emergency account (ADR-0055 3.2 puts no condition on roles held)")
    void accountWithoutRoles() throws Exception {
        String session = loginWithWecom(1004);
        browserIdentifier(1004);
        registerEmergencyAdmin(1004, "breakglass-f");
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = :id)")
                .param("id", TestAccounts.employee(1004).value()).update();

        deliver(separated(1004, LocalDate.of(2026, 10, 15)));

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(browserIdentifiers(1004)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(sessions(1004)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(emergencyEnabled(1004)).isFalse());
        assertThat(status(session)).isEqualTo(401);
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new UserId(accountOf(1004)));
            assertThat(e.cause()).isEqualTo(EmergencyAdminDisableCause.SEPARATION);
        });
    }

    @Test
    @DisplayName("#932 idempotency is per event, not per person: after a reinstatement reopened grants, a second separation (new command, new eventId) closes them again (workforce.yaml idempotencyKey eventId; ADR-0068 1.5, ADR-0055 3.6)")
    void aSecondSeparationClosesAgain() throws Exception {
        createAccount(1009, "employee", "org_data_admin");
        deliver(separated(1009, LocalDate.of(2026, 9, 1)));
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1009)).isZero());
        // The reinstatement opens the base grant again (ADR-0055 3.6); the person signs in and is recognised by a browser again.
        String session = loginWithWecom(1009);
        createAccount(1009, "employee");
        browserIdentifier(1009);
        assertThat(roles(1009)).isEqualTo(1);
        long revokedBefore = recorded().stream().filter(e -> e.action().equals(ROLE_REVOKED)).count();

        EmployeeSeparated second = separated(1009, LocalDate.of(2026, 10, 15));
        deliver(second);

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1009)).as("the second separation closes the reopened grant").isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(sessions(1009)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(browserIdentifiers(1009)).isZero());
        assertThat(status(session)).isEqualTo(401);
        assertThat(recorded().stream().filter(e -> e.action().equals(ROLE_REVOKED)).count() - revokedBefore).as("one audit for the reopened grant").isEqualTo(1);
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(identityIncomplete(second)).isZero());
    }

    @Test
    @DisplayName("#932 the same event delivered again (same eventId) changes nothing and fails nothing")
    void redeliveryIsHarmless() {
        createAccount(1005, "employee", "business_admin");
        registerEmergencyAdmin(1005, "breakglass-c");
        EmployeeSeparated event = separated(1005, LocalDate.of(2026, 10, 15));

        deliver(event);
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1005)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
        identityHandled(event);
        long firstCompletions = identityCompletions(event);
        deliver(event);

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(identityCompletions(event)).isEqualTo(2 * firstCompletions));
        assertThat(identityIncomplete(event)).isZero();
        assertThat(recorded().stream().filter(e -> e.action().equals(ROLE_REVOKED)).count()).as("each role closed once").isEqualTo(2);
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).as("the emergency account disabled once").hasSize(1);
        assertThat(domainEvents.of(AccountRolesReplaced.class)).as("the role set replaced once").hasSize(1);
        assertThat(emergencyEnabled(1005)).isFalse();
    }

    @Test
    @DisplayName("#932 nothing before the person's own event: a pending (future-dated or held) separation publishes nothing (ADR-0068 1.9), so the person keeps grants, sessions, emergency account and identifiers while other people's events are handled; their own event closes them")
    void nothingBeforeTheEvent() throws Exception {
        String session = loginWithWecom(1006);
        createAccount(1006, "super_admin");
        registerEmergencyAdmin(1006, "breakglass-d");
        browserIdentifier(1006);
        createAccount(1007, "employee");
        EmployeeSeparated someoneElse = separated(1007, LocalDate.of(2026, 10, 15));
        deliver(someoneElse);
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(identityCompletions(someoneElse)).isPositive());

        assertThat(roles(1006)).as("before its own event").isEqualTo(2);
        assertThat(sessions(1006)).isEqualTo(1);
        assertThat(status(session)).isEqualTo(200);
        assertThat(emergencyEnabled(1006)).isTrue();
        assertThat(browserIdentifiers(1006)).isEqualTo(1);
        assertThat(recorded()).filteredOn(e -> e.action().equals(ROLE_REVOKED))
                .noneMatch(e -> e.subject().equals(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(1006))));

        deliver(separated(1006, LocalDate.of(2026, 10, 20)));

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1006)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(sessions(1006)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(emergencyEnabled(1006)).isFalse());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(browserIdentifiers(1006)).isZero());
    }

    @Test
    @DisplayName("#932 nothing before the event, held path: a held separation (SeparationHeld) and its release for a future date (SeparationHoldReleased, HELD -> PENDING) leave the administrator employed, so grants, session, emergency account and identifiers stay (ADR-0068 2.2, ADR-0055 3.3); only EmployeeSeparated closes them")
    void heldAndReleasedButNotYetDueChangeNothing() throws Exception {
        String session = loginWithWecom(1010);
        createAccount(1010, "super_admin", "org_data_admin");
        registerEmergencyAdmin(1010, "breakglass-g");
        browserIdentifier(1010);
        EmployeeId employee = TestAccounts.employee(1010);
        PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());
        SeparationHoldId hold = new SeparationHoldId(UUID.randomUUID());
        LocalDate due = LocalDate.of(2027, 1, 1);
        SeparationHeld held = new SeparationHeld(TestEnvelopes.of("organization.workforce.SeparationHeld", "Employee", employee.value()), command, employee, due, hold);
        SeparationHoldReleased released = new SeparationHoldReleased(TestEnvelopes.of("organization.workforce.SeparationHoldReleased", "Employee", employee.value()),
                command, employee, due, hold, new UserId(UUID.randomUUID()));

        tx.executeWithoutResult(status -> events.publishEvent(held));
        tx.executeWithoutResult(status -> events.publishEvent(released));
        await().atMost(DELIVERY).untilAsserted(() -> {
            assertThat(incomplete(held)).isZero();
            assertThat(incomplete(released)).isZero();
        });

        assertThat(roles(1010)).as("held, then released for a future date: still employed").isEqualTo(3);
        assertThat(sessions(1010)).isEqualTo(1);
        assertThat(status(session)).isEqualTo(200);
        assertThat(emergencyEnabled(1010)).isTrue();
        assertThat(browserIdentifiers(1010)).isEqualTo(1);
        assertThat(recorded()).filteredOn(e -> e.action().equals(ROLE_REVOKED)).isEmpty();
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).isEmpty();

        deliver(new EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", employee.value()), command, employee, due, new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1"))));

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1010)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(emergencyEnabled(1010)).isFalse());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(sessions(1010)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(browserIdentifiers(1010)).isZero());
    }

    @Test
    @DisplayName("#932 a back-dated separation (effective date long past, ADR-0068 1.8 补登) closes everything on receipt and is not refused for its date")
    void backDatedSeparationClosesOnReceipt() {
        createAccount(1008, "employee", "org_data_admin");
        registerEmergencyAdmin(1008, "breakglass-e");

        EmployeeSeparated event = separated(1008, LocalDate.of(2025, 3, 1));
        deliver(event);

        await().atMost(DELIVERY).untilAsserted(() -> assertThat(roles(1008)).isZero());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(emergencyEnabled(1008)).isFalse());
        await().atMost(DELIVERY).untilAsserted(() -> assertThat(identityIncomplete(event)).isZero());
    }

    @Test
    @DisplayName("#932 a separated person without an account is a quiet no-op: the identity listener completes, no account is opened, nothing is revoked")
    void noAccountIsAQuietNoOp() {
        knownEmployee(5555, EmploymentStatus.SEPARATED);
        EmployeeSeparated event = separated(5555, LocalDate.of(2026, 10, 15));

        deliver(event);

        identityHandled(event);
        assertThat(identityIncomplete(event)).isZero();
        assertThat(count("user_accounts")).isZero();
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(recorded()).noneMatch(e -> e.action().equals(ROLE_REVOKED) || e.action().equals("platform.identity.session.revoked"));
    }
}
