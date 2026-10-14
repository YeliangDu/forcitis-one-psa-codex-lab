package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Opens the request for a change that needs a second person; what needs one is decided by SensitivePermissions. */
@Component
class AuthorizationGate {

    private final AuthorizationRequestRepository requests;
    private final DomainEvents events;
    private final Clock clock;

    AuthorizationGate(AuthorizationRequestRepository requests, DomainEvents events, Clock clock) {
        this.requests = requests;
        this.events = events;
        this.clock = clock;
    }

    AuthorizationRequestId openReplaceRoles(UserId by, UserId subject, Set<String> roleCodes) {
        return open(AuthorizationRequest.replaceRoles(newId(), by, clock.instant(), subject, roleCodes));
    }

    AuthorizationRequestId openCreateRole(UserId by, RoleDraft draft) {
        return open(AuthorizationRequest.createRole(newId(), by, clock.instant(), draft));
    }

    AuthorizationRequestId openUpdateRole(UserId by, RoleId role, RoleDraft draft) {
        return open(AuthorizationRequest.updateRole(newId(), by, clock.instant(), role, draft));
    }

    private AuthorizationRequestId newId() {
        return new AuthorizationRequestId(Uuids.timeOrdered(clock));
    }

    private AuthorizationRequestId open(AuthorizationRequest request) {
        requests.insert(request);
        events.publish(request);
        return request.requestId();
    }
}
