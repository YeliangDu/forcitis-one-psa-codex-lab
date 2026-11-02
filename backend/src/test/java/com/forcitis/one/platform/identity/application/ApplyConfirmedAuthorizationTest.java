package com.forcitis.one.platform.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.events.AuthorizationConfirmed;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

@DisplayName("ApplyConfirmedAuthorization: a redelivery after the change was applied does nothing, and the base is compared only after the role rows are locked")
class ApplyConfirmedAuthorizationTest {

    static final UserId REQUESTER = TestAccounts.account(1);
    static final UserId CONFIRMER = TestAccounts.account(2);
    static final RoleVersionId BASE_VERSION = new RoleVersionId(UUID.randomUUID());

    final AuthorizationRequestRepository requests = mock(AuthorizationRequestRepository.class);
    final CreateRole createRole = mock(CreateRole.class);
    final UpdateRole updateRole = mock(UpdateRole.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final DomainEvents events = mock(DomainEvents.class);
    final AuthorizationVoiding voiding = new AuthorizationVoiding(roles, mock(com.forcitis.one.platform.audit.api.AuditLog.class));
    final ApplyConfirmedAuthorization listener = new ApplyConfirmedAuthorization(requests, createRole, updateRole, voiding, roles, events);

    private AuthorizationRequest pendingRoleChange() {
        RoleId role = new RoleId(UUID.randomUUID());
        AuthorizationRequest request = AuthorizationRequest.updateRole(new AuthorizationRequestId(UUID.randomUUID()), REQUESTER, Instant.parse("2026-11-02T08:00:00Z"),
                role, new RoleDraft("synthetic", "Synthetic", true, AccessScopeKind.ALL, Set.of("platform.approval:task:read"), Set.of()),
                new AuthorizationBase(null, List.of(BASE_VERSION)));
        when(requests.find(request.requestId())).thenReturn(Optional.of(request));
        return request;
    }

    private AuthorizationRequest confirmedRoleChange() {
        AuthorizationRequest request = pendingRoleChange();
        request.confirm(CONFIRMER, Instant.parse("2026-11-02T08:01:00Z"));
        request.drainEvents(TestEnvelopes.INSTANCE);
        return request;
    }

    private void deliver(AuthorizationRequest request) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, null)) {
            listener.on(new AuthorizationConfirmed(TestEnvelopes.of("AuthorizationConfirmed", "AuthorizationRequest", request.requestId().value()), request.requestId(), CONFIRMER));
        }
    }

    @Test
    @DisplayName("the role already has a version written by the request (its own apply moved the base): the redelivery neither voids nor applies again")
    void aRedeliveryAfterTheApplyChangesNothing() {
        AuthorizationRequest request = confirmedRoleChange();
        when(roles.versionWrittenBy(request.requestId())).thenReturn(true);

        deliver(request);

        verify(roles).lockRolesOf(List.of(BASE_VERSION), true);
        verify(roles).versionWrittenBy(request.requestId());
        verifyNoMoreInteractions(roles);
        verify(requests).find(request.requestId());
        verifyNoMoreInteractions(requests);
        verifyNoInteractions(events, updateRole, createRole);
    }

    @Test
    @DisplayName("a request that is already VOIDED or REJECTED when the confirmation is delivered again is left alone: nothing locked, saved, published or applied")
    void aRequestThatIsNoLongerConfirmedIsLeftAlone() {
        AuthorizationRequest voided = confirmedRoleChange();
        voided.markVoided();
        AuthorizationRequest rejected = pendingRoleChange();
        rejected.reject(CONFIRMER, Instant.parse("2026-11-02T08:01:00Z"));
        rejected.drainEvents(TestEnvelopes.INSTANCE);

        deliver(voided);
        deliver(rejected);

        verifyNoInteractions(roles, events, updateRole, createRole);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("a role change is compared with the role rows locked for writing first, never before")
    void theRoleRowsAreLockedBeforeTheVersionsAreRead() {
        AuthorizationRequest request = confirmedRoleChange();
        when(roles.currentVersions(List.of(BASE_VERSION))).thenReturn(Set.of(BASE_VERSION));

        deliver(request);

        InOrder order = Mockito.inOrder(roles, updateRole);
        order.verify(roles).lockRolesOf(List.of(BASE_VERSION), true);
        order.verify(roles).versionWrittenBy(request.requestId());
        order.verify(roles).currentVersions(List.of(BASE_VERSION));
        order.verify(updateRole).applyConfirmed(any(), any(), any(), any());
    }
}
