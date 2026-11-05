package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.AuthorizationConfirmed;
import com.forcitis.one.platform.identity.events.AuthorizationRejected;
import com.forcitis.one.platform.identity.events.AuthorizationVoided;
import com.forcitis.one.platform.identity.events.AuthorizationOpened;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A change that hands out, or changes a role to carry, a sensitive permission waits here until a second person confirms it: not
 * the requester, and not the person who would receive the roles. The change is applied after the confirmation, by a listener.
 */
public final class AuthorizationRequest extends AggregateRoot {

    public static final StateMachine<AuthorizationRequestStatus> MACHINE = StateMachine.of(AuthorizationRequestStatus.class)
            .allow(AuthorizationRequestStatus.PENDING, AuthorizationRequestStatus.CONFIRMED, AuthorizationRequestStatus.REJECTED, AuthorizationRequestStatus.VOIDED)
            .allow(AuthorizationRequestStatus.CONFIRMED, AuthorizationRequestStatus.VOIDED)
            .build();

    private final AuthorizationRequestId requestId;
    private final AuthorizationRequestKind kind;
    private final UserId requestedBy;
    private final Instant requestedAt;
    private final @Nullable UserId subjectUser;
    private final @Nullable RoleId roleId;
    private final Set<String> roleCodes;
    private final @Nullable RoleDraft draft;
    private final AuthorizationBase base;
    private final AuthorizationRequestStatus loadedStatus;
    private AuthorizationRequestStatus status;
    private @Nullable UserId decidedBy;
    private @Nullable Instant decidedAt;
    private final boolean stored;

    private AuthorizationRequest(AuthorizationRequestId requestId, AuthorizationRequestKind kind, UserId requestedBy, Instant requestedAt,
            @Nullable UserId subjectUser, @Nullable RoleId roleId, Set<String> roleCodes, @Nullable RoleDraft draft,
            AuthorizationBase base, AuthorizationRequestStatus status, @Nullable UserId decidedBy, @Nullable Instant decidedAt, boolean stored, long version,
            long eventSequence) {
        super(version, eventSequence);
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.subjectUser = subjectUser;
        this.roleId = roleId;
        this.roleCodes = Set.copyOf(roleCodes);
        this.draft = draft;
        this.base = Objects.requireNonNull(base, "base");
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.stored = stored;
    }

    public static AuthorizationRequest replaceRoles(AuthorizationRequestId id, UserId requestedBy, Instant at, UserId subject, Set<String> roleCodes,
            AuthorizationBase base) {
        return opened(new AuthorizationRequest(id, AuthorizationRequestKind.REPLACE_ROLES, requestedBy, at, subject, null, roleCodes, null, base,
                AuthorizationRequestStatus.PENDING, null, null, false, 0, 0));
    }

    public static AuthorizationRequest createRole(AuthorizationRequestId id, UserId requestedBy, Instant at, RoleDraft draft) {
        return opened(new AuthorizationRequest(id, AuthorizationRequestKind.CREATE_ROLE, requestedBy, at, null, null, Set.of(), draft,
                AuthorizationBase.NONE, AuthorizationRequestStatus.PENDING, null, null, false, 0, 0));
    }

    public static AuthorizationRequest updateRole(AuthorizationRequestId id, UserId requestedBy, Instant at, RoleId role, RoleDraft draft,
            AuthorizationBase base) {
        return opened(new AuthorizationRequest(id, AuthorizationRequestKind.UPDATE_ROLE, requestedBy, at, null, role, Set.of(), draft, base,
                AuthorizationRequestStatus.PENDING, null, null, false, 0, 0));
    }

    public static AuthorizationRequest restore(AuthorizationRequestId id, AuthorizationRequestKind kind, UserId requestedBy, Instant requestedAt,
            @Nullable UserId subjectUser, @Nullable RoleId roleId, Set<String> roleCodes, @Nullable RoleDraft draft, AuthorizationBase base,
            String status, @Nullable UserId decidedBy, @Nullable Instant decidedAt, long version, long eventSequence) {
        return new AuthorizationRequest(id, kind, requestedBy, requestedAt, subjectUser, roleId, roleCodes, draft, base,
                AuthorizationRequestStatus.valueOf(status), decidedBy, decidedAt, true, version, eventSequence);
    }

    private static AuthorizationRequest opened(AuthorizationRequest request) {
        request.record((envelopes, version) -> new AuthorizationOpened(envelopes.wrap(request.draft("AuthorizationOpened", version)),
                request.requestId, request.requestedBy));
        return request;
    }

    public void confirm(UserId by, Instant at) {
        confirm(by, at, Set.of());
    }

    /** A holder of the role whose definition is changed gains from the change as well, so is no second person either. */
    public void confirm(UserId by, Instant at, Set<RoleId> rolesHeldByConfirmer) {
        requirePending();
        if (by.equals(requestedBy) || by.equals(subjectUser) || (roleId != null && rolesHeldByConfirmer.contains(roleId))) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED, ErrorKind.FORBIDDEN,
                    "the requester, the person who would receive the roles and a holder of the role being changed cannot confirm");
        }
        this.status = MACHINE.require(status, AuthorizationRequestStatus.CONFIRMED, IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        this.decidedBy = by;
        this.decidedAt = at;
        record((envelopes, version) -> new AuthorizationConfirmed(envelopes.wrap(draft("AuthorizationConfirmed", version)), requestId, by));
    }

    /** Nothing is applied and the request ends here: its base moved between the request and its execution, or (from PENDING) the subject or the requester separated (#1322). */
    public void markVoided() {
        this.status = MACHINE.require(status, AuthorizationRequestStatus.VOIDED, IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        record((envelopes, version) -> new AuthorizationVoided(envelopes.wrap(draft("AuthorizationVoided", version)), requestId));
    }

    public void reject(UserId by, Instant at) {
        requirePending();
        this.status = MACHINE.require(status, AuthorizationRequestStatus.REJECTED, IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING);
        this.decidedBy = by;
        this.decidedAt = at;
        record((envelopes, version) -> new AuthorizationRejected(envelopes.wrap(draft("AuthorizationRejected", version)), requestId, by));
    }

    private void requirePending() {
        if (status != AuthorizationRequestStatus.PENDING) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING, ErrorKind.CONFLICT,
                    "the request is already " + status);
        }
    }

    public AuthorizationRequestId requestId() {
        return requestId;
    }

    public AuthorizationRequestKind kind() {
        return kind;
    }

    public UserId requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public @Nullable UserId subjectUser() {
        return subjectUser;
    }

    public @Nullable RoleId roleId() {
        return roleId;
    }

    public Set<String> roleCodes() {
        return roleCodes;
    }

    public @Nullable RoleDraft roleDraft() {
        return draft;
    }

    public AuthorizationBase base() {
        return base;
    }

    public AuthorizationRequestStatus status() {
        return status;
    }

    public AuthorizationRequestStatus loadedStatus() {
        return loadedStatus;
    }

    public @Nullable UserId decidedBy() {
        return decidedBy;
    }

    public @Nullable Instant decidedAt() {
        return decidedAt;
    }

    public boolean isStored() {
        return stored;
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "AuthorizationRequest", requestId.value(), version, null, null, List.of());
    }
}
