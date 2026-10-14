package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

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

    private void publish(List<UserId> changed, List<UserId> separated) {
        tx.executeWithoutResult(status -> events.publishEvent(
                new OrgSnapshotChanged(TestEnvelopes.of("organization.workforce.OrgSnapshotChanged", "SyncRun", "1"), changed, separated, List.of())));
    }

    private long roleCount(long userId) {
        return jdbc.sql("select count(*) from platform_identity.account_roles where user_id = :id").param("id", userId).query(Long.class).single();
    }

    private void registerEmergencyAdmin(long userId, String name) {
        emergencyAdmins.insertIfAbsent(EmergencyAdmin.register(new UserId(userId), name, hasher.hash("Str0ng!Passphrase#42"), vault.seal(Totp.newSecret())));
    }

    @Test
    @DisplayName("a separated person loses every role (super_admin included) with one audit entry each, the emergency account and every session")
    void separationRemovesRolesEmergencyAccountAndSessions() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);
        createAccount(1001, "super_admin", "org_data_admin");
        registerEmergencyAdmin(1001, "breakglass-a");
        registerEmergencyAdmin(1002, "breakglass-b");
        accessCache.evictAll();

        publish(List.of(), List.of(new UserId(1001)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleCount(1001)).isZero());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
        assertThat(roleCount(1002)).as("nobody else is touched").isEqualTo(1);
        assertThat(jdbc.sql("select is_enabled from platform_identity.emergency_admins where user_id = 1001").query(Boolean.class).single()).isFalse();
        assertThat(jdbc.sql("select is_enabled from platform_identity.emergency_admins where user_id = 1002").query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("select count(*) from platform_identity.sessions where principal_name = '1001'").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from platform_identity.sessions where principal_name = '1002'").query(Long.class).single()).isEqualTo(1);
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.account.role.revoked"))
                .extracting(AuditEntry::detail)
                .containsExactlyInAnyOrder("role=employee cause=separation", "role=org_data_admin cause=separation", "role=super_admin cause=separation");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.account.role.revoked"))
                .allSatisfy(entry -> assertThat(entry.subjectKey()).isEqualTo("user-account:1001"));
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.session.revoked");
        assertThat(domainEvents.of(AccountRolesReplaced.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new UserId(1001));
            assertThat(e.roleIds()).isEmpty();
        });
        assertThat(domainEvents.of(EmergencyAdminDisabled.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(new UserId(1001));
            assertThat(e.cause()).isEqualTo(EmergencyAdminDisableCause.SEPARATION);
        });
        assertThat(mvcStatus(victim)).isEqualTo(401);
        assertThat(mvcStatus(other)).isEqualTo(200);
    }

    @Test
    @DisplayName("a person reported as no longer employed in a change event is handled the same way")
    void changedPersonWhoIsNoLongerEmployedLosesTheRoles() throws Exception {
        knownEmployee(1003, EmploymentStatus.SEPARATED);
        createAccount(1003, "employee", "business_admin");

        publish(List.of(new UserId(1003)), List.of());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleCount(1003)).isZero());
    }

    @Test
    @DisplayName("an event delivered again changes nothing and fails nothing")
    void deliveringTheEventTwiceIsHarmless() {
        createAccount(1004, "employee");
        registerEmergencyAdmin(1004, "breakglass-c");

        publish(List.of(), List.of(new UserId(1004)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleCount(1004)).isZero());
        publish(List.of(), List.of(new UserId(1004)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(recorded().stream().filter(e -> e.action().equals("platform.identity.session.revoked")).count()).isEqualTo(2));
        assertThat(recorded().stream().filter(e -> e.action().equals("platform.identity.account.role.revoked")).count())
                .as("the roles were removed once").isEqualTo(1);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(domainEvents.of(EmergencyAdminDisabled.class)).hasSize(1));
    }

    @Test
    @DisplayName("a person without an account is a quiet no-op for the roles")
    void personWithoutAnAccount() {
        publish(List.of(), List.of(new UserId(5555)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(recorded().stream().filter(e -> e.subjectKey().equals("user-account:5555")).count()).isEqualTo(1));
        assertThat(count("account_roles")).isZero();
    }

    private int mvcStatus(String session) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(SessionLifecycleIntegrationTest.CURRENT)
                .cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }
}
