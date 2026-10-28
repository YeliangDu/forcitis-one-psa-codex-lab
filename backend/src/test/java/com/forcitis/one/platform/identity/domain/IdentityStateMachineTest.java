package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static com.forcitis.one.platform.identity.domain.AccountStatus.DISABLED;
import static com.forcitis.one.platform.identity.domain.AccountStatus.ENABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.events.AccountDisabled;
import com.forcitis.one.platform.identity.events.AccountEnabled;
import com.forcitis.one.platform.identity.events.AccountOpened;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import com.forcitis.one.platform.identity.events.RoleCreated;
import com.forcitis.one.platform.identity.events.RoleDeleted;
import com.forcitis.one.platform.identity.events.RoleDisabled;
import com.forcitis.one.platform.identity.events.RoleEnabled;
import com.forcitis.one.platform.identity.events.RoleRevised;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityStateMachineTest {

    static UserAccount account(AccountStatus status) {
        return UserAccount.restore(TestAccounts.account(1001), TestAccounts.employee(1001), status.name(), Set.of(), 5, 5);
    }

    static Role role(RoleStatus status) {
        return Role.restore(new RoleId(TestIds.uuid(7)), new RoleCode("analyst"), "CUSTOM", false, new RoleVersionId(TestIds.uuid(70)), "Analyst", status.name(),
                AccessScopeKind.SELF, Set.of(), 2, 2);
    }

    @Test
    @DisplayName("account: enable and disable are tried from every status; only the opposite status is legal")
    void accountTransitions() {
        new TransitionTable<AccountStatus, UserAccount>(AccountStatus.class, IdentityStateMachineTest::account, UserAccount::status)
                .operation("enable", UserAccount::enable, DISABLED, ENABLED)
                .operation("disable", UserAccount::disable, ENABLED, DISABLED)
                .verify();
    }

    @Test
    @DisplayName("role: enable and disable are tried from every status; only the opposite status is legal")
    void roleTransitions() {
        new TransitionTable<RoleStatus, Role>(RoleStatus.class, IdentityStateMachineTest::role, Role::status)
                .operation("enable", Role::enable, RoleStatus.DISABLED, RoleStatus.ENABLED)
                .operation("disable", Role::disable, RoleStatus.ENABLED, RoleStatus.DISABLED)
                .verify();
    }

    @Test
    @DisplayName("a system role is frozen: neither a transition nor a revision nor a deletion is possible, in any status")
    void systemRoleIsFrozen() {
        for (RoleStatus status : RoleStatus.values()) {
            Role system = Role.restore(new RoleId(TestIds.uuid(1)), new RoleCode("employee"), "SYSTEM", false, new RoleVersionId(TestIds.uuid(10)), "Employee",
                    status.name(), AccessScopeKind.SELF, Set.of(), 0, 0);
            for (Runnable change : List.<Runnable>of(system::enable, system::disable, system::delete, () -> system.declareEnabled(true),
                    () -> system.revise("x", AccessScopeKind.SELF, Set.of()))) {
                assertThatThrownBy(change::run).isInstanceOfSatisfying(RoleRuleException.class,
                        e -> assertThat(e.violation()).isEqualTo(RoleViolation.SYSTEM_FROZEN));
            }
            assertThat(system.hasPendingEvents()).isFalse();
        }
    }

    @Test
    @DisplayName("declaring the state a role or an account already has changes nothing and records nothing")
    void declaringTheCurrentStateIsANoOp() {
        UserAccount enabled = account(ENABLED);
        enabled.declareEnabled(true);
        assertThat(enabled.hasPendingEvents()).isFalse();
        enabled.declareEnabled(false);
        assertThat(enabled.status()).isEqualTo(DISABLED);

        Role disabled = role(RoleStatus.DISABLED);
        disabled.declareEnabled(false);
        assertThat(disabled.hasPendingEvents()).isFalse();
        disabled.declareEnabled(true);
        assertThat(disabled.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("each change records its own event with the aggregate and the versions after the loaded one")
    void events() {
        UserAccount account = account(ENABLED);
        account.disable();
        account.replaceRoles(Set.of(new RoleId(TestIds.uuid(9)), new RoleId(TestIds.uuid(3))));
        List<DomainEvent> accountEvents = account.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(accountEvents.get(0)).isInstanceOf(AccountDisabled.class);
        assertThat(accountEvents.get(1)).isInstanceOfSatisfying(AccountRolesReplaced.class,
                e -> assertThat(e.roleIds()).containsExactly(new RoleId(TestIds.uuid(3)), new RoleId(TestIds.uuid(9))));
        assertThat(accountEvents).extracting(e -> e.envelope().aggregateVersion()).containsExactly(6L, 7L);
        assertThat(accountEvents.get(0).envelope().aggregateType()).isEqualTo("UserAccount");
        assertThat(accountEvents.get(0).envelope().aggregateId()).isEqualTo(TestAccounts.account(1001).value());
        assertThat(accountEvents.get(0).envelope().legalEntityId()).isNull();

        UserAccount again = account(DISABLED);
        again.enable();
        assertThat(again.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(AccountEnabled.class);

        Role role = role(RoleStatus.ENABLED);
        role.revise("Senior", AccessScopeKind.SELF, Set.of());
        role.disable();
        role.enable();
        role.delete();
        assertThat(role.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName())
                .containsExactly(RoleRevised.class.getSimpleName(), RoleDisabled.class.getSimpleName(), RoleEnabled.class.getSimpleName(),
                        RoleDeleted.class.getSimpleName());

        Role created = Role.newCustom(new RoleId(TestIds.uuid(11)), new RoleCode("analyst"), "Analyst", Set.of());
        created.versionWritten(new RoleVersionId(TestIds.uuid(12)));
        assertThat(created.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(RoleCreated.class, e -> {
            assertThat(e.roleId()).isEqualTo(new RoleId(TestIds.uuid(11)));
            assertThat(e.roleVersionId()).as("ADR-0034-IMPL E1: the event names the version the repository wrote").isEqualTo(new RoleVersionId(TestIds.uuid(12)));
        });
    }

    @Test
    @DisplayName("opening an account is enabled from the start, holds the given roles and announces AccountOpened")
    void opening() {
        UserAccount opened = UserAccount.open(TestAccounts.account(2002), TestAccounts.employee(2002), Set.of(new RoleId(TestIds.uuid(4)), new RoleId(TestIds.uuid(2))));

        assertThat(opened.status()).isEqualTo(ENABLED);
        assertThat(opened.isStored()).isFalse();
        assertThat(opened.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(AccountOpened.class, e -> {
            assertThat(e.roleIds()).containsExactly(new RoleId(TestIds.uuid(2)), new RoleId(TestIds.uuid(4)));
            assertThat(e.envelope().aggregateVersion()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("B1-M3 separating an account takes every role away and records the fact; an account without roles records nothing")
    void separationRecordsTheFact() {
        UserAccount withRoles = UserAccount.restore(TestAccounts.account(1001), TestAccounts.employee(1001), ENABLED.name(), Set.of(new RoleId(TestIds.uuid(3))), 5, 5);
        withRoles.recordSeparation();
        assertThat(withRoles.roles()).isEmpty();
        assertThat(withRoles.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("AccountRolesReplaced", "AccountSeparated");

        UserAccount bare = account(ENABLED);
        bare.recordSeparation();
        assertThat(bare.hasPendingEvents()).isFalse();
    }
}
