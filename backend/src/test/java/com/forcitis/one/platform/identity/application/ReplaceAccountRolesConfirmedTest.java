package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReplaceAccountRoles.applyConfirmed: a requested role that no longer exists is a moved base, so the request is voided, not refused and left pending")
class ReplaceAccountRolesConfirmedTest {

    static final UserId REQUESTER = TestAccounts.account(1);
    static final UserId SUBJECT = TestAccounts.account(2);

    final AccountRepository accounts = mock(AccountRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final AccountGrants grants = mock(AccountGrants.class);
    final ReplaceAccountRoles replace = new ReplaceAccountRoles(accounts, roles, mock(PrivilegeCeiling.class), mock(SessionRevocation.class), mock(AccessCache.class),
            mock(DomainEvents.class), mock(CurrentUser.class), mock(AuthorizationGate.class), mock(AuditLog.class), grants, mock(AdministratorFloor.class),
            mock(AdministratorFloorNotice.class), mock(AuthorizationVoiding.class));

    @Test
    void aDeletedRequestedRoleEndsTheConfirmationAsMoved() {
        when(accounts.find(SUBJECT)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(roles.findByCode(any())).thenReturn(Optional.empty());
        AuthorizationRequest request = AuthorizationRequest.replaceRoles(new AuthorizationRequestId(UUID.randomUUID()), REQUESTER, Instant.parse("2026-11-02T08:00:00Z"),
                SUBJECT, Set.of("deleted_since"), new AuthorizationBase(1L, List.of()));

        AdminOutcome<Boolean> outcome = replace.applyConfirmed(SUBJECT, Set.of("deleted_since"), request);

        assertThat(outcome).isEqualTo(new AdminOutcome.Done<>(false));
        verifyNoInteractions(grants);
    }
}
