package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import com.forcitis.one.platform.identity.events.EmergencyAdminDisabled;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;

@DisplayName("SeparationRevokesRoles: ADR-0027 section 1 item 6 (RULE-363)")
class SeparationRevokesRolesIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EmergencyAdminRepository emergencyAdmins;

    @Autowired
    PasswordHasher hasher;

    @Autowired
    TotpSecretVault vault;

    private UUID publish(EmployeeId separated) {
        var event = new com.forcitis.one.organization.workforce.events.EmployeeSeparated(TestEnvelopes.of("organization.workforce.EmployeeSeparated", "Employee", separated.value()), new com.forcitis.one.organization.workforce.api.PersonnelCommandId(java.util.UUID.randomUUID()), separated, java.time.LocalDate.of(2026, 10, 15), new com.forcitis.one.organization.workforce.api.EmploymentRelationshipId(java.util.UUID.fromString("019a0000-0000-7000-8000-0000000000e1")));
        tx.executeWithoutResult(status -> events.publishEvent(event));
        return event.envelope().eventId();
    }

    private long roleCount(long userId) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = :id)").param("id", TestAccounts.employee(userId).value()).query(Long.class).single();
    }

    private void registerEmergencyAdmin(long userId, String name) {
        emergencyAdmins.insertIfAbsent(EmergencyAdmin.register(new com.forcitis.one.kernel.UserId(accountOf(userId)), name, hasher.hash(UUID.randomUUID().toString()), vault.seal(Totp.newSecret())));
    }

    @Test
    @DisplayName("B1-M3 a separated person loses every role (super_admin included) with one audit entry each, the emergency account and every session")
    void separationRemovesRolesEmergencyAccountAndSessions() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);
        createAccount(1001, "super_admin", "org_data_admin");
        registerEmergencyAdmin(1001, "breakglass-a");
        registerEmergencyAdmin(1002, "breakglass-b");
        accessCache.evictAll();

        long grantRows = count("account_role_grants");
        publish(TestAccounts.employee(1001));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleCount(1001)).isZero());
        assertThat(count("account_role_grants")).as("closed, never deleted: the grant total is unchanged").isEqualTo(grantRows);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
        assertThat(roleCount(1002)).as("nobody else is touched").isEqualTo(1);
        assertThat(jdbc.sql("select is_enabled from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(1001) + ")").query(Boolean.class).single()).isFalse();
        assertThat(jdbc.sql("select is_enabled from platform_identity.emergency_admins where user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(1002) + ")").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("select count(*) from platform_identity.sessions where principal_name = (select ua.user_id::text from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(1001) + ")").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from platform_identity.sessions where principal_name = (select ua.user_id::text from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(1002) + ")").query(Long.class).single()).isEqualTo(1);
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.account.role.revoked"))
                .extracting(entry -> entry.detail().replaceAll("^grant=\\S+ (role=\\S+ cause=SEPARATION) at=\\S+$", "$1"))
                .containsExactlyInAnyOrder("role=employee cause=SEPARATION", "role=org_data_admin cause=SEPARATION", "role=super_admin cause=SEPARATION");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.account.role.revoked"))
                .allSatisfy(entry -> assertThat(entry.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(1001))));
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.session.revoked");
        assertThat(domainEvents.of(AccountRolesReplaced.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new com.forcitis.one.kernel.UserId(accountOf(1001)));
            assertThat(e.roleIds()).isEmpty();
        });
        assertThat(domainEvents.of(com.forcitis.one.platform.identity.events.AccountSeparated.class)).singleElement()
                .satisfies(e -> assertThat(e.userId()).isEqualTo(new com.forcitis.one.kernel.UserId(accountOf(1001))));
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new com.forcitis.one.kernel.UserId(accountOf(1001)));
            assertThat(e.cause()).isEqualTo(EmergencyAdminDisableCause.SEPARATION);
        });
        assertThat(mvcStatus(victim)).isEqualTo(401);
        assertThat(mvcStatus(other)).isEqualTo(200);
    }

    @Test
    @DisplayName("an event delivered again changes nothing and fails nothing")
    void deliveringTheEventTwiceIsHarmless() {
        createAccount(1004, "employee");
        registerEmergencyAdmin(1004, "breakglass-c");

        publish(TestAccounts.employee(1004));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleCount(1004)).isZero());
        publish(TestAccounts.employee(1004));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(recorded().stream().filter(e -> e.action().equals("platform.identity.session.revoked")).count()).isEqualTo(2));
        assertThat(recorded().stream().filter(e -> e.action().equals("platform.identity.account.role.revoked")).count())
                .as("the roles were removed once").isEqualTo(1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
    }

    @Test
    @DisplayName("P2-H3 a person without an account is a quiet no-op: once the separation listener has handled the event, no account is opened, no role and no session is revoked (R-1b P2: there is no account id to name)")
    void P2_H3_noAccountIsOpenedForASeparatedPersonWithout() {
        UUID published = publish(TestAccounts.employee(5555));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.sql("""
                select count(*) from (select listener_id, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like '%HandleEmployeeSeparated%' and p.completion_date is not null and p.serialized_event like :event
                """).param("event", "%" + published + "%").query(Long.class).single()).isEqualTo(1));
        assertThat(count("user_accounts")).isZero();
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(recorded()).noneMatch(e -> e.action().equals("platform.identity.session.revoked"));
    }

    private int mvcStatus(String session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(SessionLifecycleIntegrationTest.CURRENT)
                .cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }
}
