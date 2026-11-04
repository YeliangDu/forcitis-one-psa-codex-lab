package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1244: the scope of a two-person role is checked again when a confirmed request is applied. Over the API the request cannot reach this refusal with an unchanged base
 * (the same check ran when it was opened), so it has no acceptance case; this test goes red when the check is removed from either use case.
 */
@DisplayName("#1244 applyConfirmed re-checks the scope a two-person role may have")
class ConfirmedRoleScopeRefusalTest {

    static final UserId REQUESTER = TestAccounts.account(1);
    static final Set<String> TWO_PERSON = Set.of(Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);

    final RoleRepository roles = mock(RoleRepository.class);
    final DomainEvents events = mock(DomainEvents.class);
    final AuditLog audit = mock(AuditLog.class);
    final PrivilegeCeiling ceiling = mock(PrivilegeCeiling.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final AuthorizationGate gate = mock(AuthorizationGate.class);
    final UpdateRole updateRole = new UpdateRole(roles, ceiling, mock(AccessCache.class), events, currentUser, mock(AccountRepository.class), gate, audit);
    final IdGenerator ids = mock(IdGenerator.class);
    final CreateRole createRole = new CreateRole(roles, mock(PrivilegeCeiling.class), mock(AccessCache.class), events, mock(CurrentUser.class), mock(AuthorizationGate.class),
            ids, audit);

    @Test
    @DisplayName("a confirmed update that leaves a two-person role with a scope that follows the job is refused, nothing is stored")
    void anUpdateIsRefused() {
        Role role = Role.newCustom(new RoleId(UUID.randomUUID()), new RoleCode("f1-confirmed-update"), "Synthetic", TWO_PERSON);
        when(roles.findById(role.roleId())).thenReturn(Optional.of(role));

        AdminOutcome<RoleView> outcome = updateRole.applyConfirmed(role.roleId(),
                new SaveRoleCommand("f1-confirmed-update", "Synthetic", true, AccessScopeKind.OWN_ORG_UNIT, TWO_PERSON, null), REQUESTER,
                new AuthorizationRequestId(UUID.randomUUID()));

        assertThat(outcome).isInstanceOfSatisfying(AdminOutcome.Refused.class, refused -> assertThat(refused.error()).isEqualTo(IdentityError.PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED));
        verify(roles, never()).update(any(), any());
    }

    @Test
    @DisplayName("a confirmed create of a two-person role with a scope that follows the job is refused, nothing is stored")
    void aCreateIsRefused() {
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(roles.insert(any(), any())).thenAnswer(call -> call.getArgument(0));
        AdminOutcome<RoleView> outcome = createRole.applyConfirmed(
                new SaveRoleCommand("f1-confirmed-create", "Synthetic", true, AccessScopeKind.OWN_ORG_UNIT, TWO_PERSON, null), REQUESTER, new AuthorizationRequestId(UUID.randomUUID()));

        assertThat(outcome).isInstanceOfSatisfying(AdminOutcome.Refused.class, refused -> assertThat(refused.error()).isEqualTo(IdentityError.PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED));
        verify(roles, never()).insert(any(), any());
    }

    private Role storedTwoPersonRole(AccessScopeKind scope, String status) {
        return Role.restore(new RoleId(UUID.randomUUID()), new RoleCode("f1-widening"), "CUSTOM", false, new RoleVersionId(UUID.randomUUID()), "Synthetic", status, scope, TWO_PERSON,
                Set.of(), 0, 0);
    }

    @Test
    @DisplayName("widening the scope kind or enabling a disabled two-person role, with the same codes, waits for a second person instead of being stored")
    void wideningWaitsForASecondPerson() {
        when(currentUser.userId()).thenReturn(REQUESTER);
        when(ceiling.mayRequest(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
        when(gate.openUpdateRole(any(), any(), any(), any())).thenReturn(new AuthorizationRequestId(UUID.randomUUID()));
        for (Role role : new Role[] {storedTwoPersonRole(AccessScopeKind.SELF, "ENABLED"), storedTwoPersonRole(AccessScopeKind.SELF, "DISABLED")}) {
            when(roles.findById(role.roleId())).thenReturn(Optional.of(role));

            AdminOutcome<RoleView> outcome = updateRole.execute(role.roleId(), com.forcitis.one.kernel.ExpectedVersion.of(0),
                    new SaveRoleCommand("f1-widening", "Synthetic", true, role.status().name().equals("ENABLED") ? AccessScopeKind.ALL : AccessScopeKind.SELF, TWO_PERSON, null));

            assertThat(outcome).isInstanceOf(AdminOutcome.Awaiting.class);
        }
        verify(roles, never()).update(any(), any());
    }

    @Test
    @DisplayName("narrowing a two-person role, or a change that widens nothing, is stored at once")
    void narrowingIsStored() {
        Role role = storedTwoPersonRole(AccessScopeKind.ALL, "ENABLED");
        when(currentUser.userId()).thenReturn(REQUESTER);
        when(ceiling.mayGrant(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
        when(roles.findById(role.roleId())).thenReturn(Optional.of(role));

        AdminOutcome<RoleView> outcome = updateRole.execute(role.roleId(), com.forcitis.one.kernel.ExpectedVersion.of(0),
                new SaveRoleCommand("f1-widening", "Synthetic", true, AccessScopeKind.SELF, TWO_PERSON, null));

        assertThat(outcome).isInstanceOf(AdminOutcome.Done.class);
        verify(roles).update(any(), any());
    }
}
