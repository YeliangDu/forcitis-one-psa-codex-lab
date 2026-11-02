package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.events.AuthorizationConfirmed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** After the second person confirmed: a role definition change takes effect in its own transaction (a role replacement was applied by the confirmation itself). A failure stays visible and is redelivered. */
@SystemActorName("platform.identity.authorization")
@Component
class ApplyConfirmedAuthorization {

    private static final Logger log = LoggerFactory.getLogger(ApplyConfirmedAuthorization.class);

    private final AuthorizationRequestRepository requests;
    private final CreateRole createRole;
    private final UpdateRole updateRole;

    ApplyConfirmedAuthorization(AuthorizationRequestRepository requests, CreateRole createRole, UpdateRole updateRole) {
        this.requests = requests;
        this.createRole = createRole;
        this.updateRole = updateRole;
    }

    @ApplicationModuleListener
    void on(AuthorizationConfirmed event) {
        AuthorizationRequest request = requests.find(event.requestId()).orElseThrow();
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                "platform.identity.authorization")) {
            AdminOutcome<?> outcome = switch (request.kind()) {
                case REPLACE_ROLES -> null;
                case CREATE_ROLE -> createRole.applyConfirmed(toCommand(request), request.requestedBy(), request.requestId());
                case UPDATE_ROLE -> updateRole.applyConfirmed(request.roleId(), toCommand(request), request.requestedBy(), request.requestId());
            };
            if (outcome instanceof AdminOutcome.Refused<?> refused) {
                log.error("ALERT platform.identity the confirmed authorization request {} could not be applied: {}", request.requestId().value(),
                        refused.error());
            }
        }
    }

    /** The request's units are all without descendants; a request that names none keeps (or, on create, leaves out) the role's units. */
    private static SaveRoleCommand toCommand(AuthorizationRequest request) {
        var draft = request.roleDraft();
        return new SaveRoleCommand(draft.code(), draft.name(), draft.enabled(), draft.accessScope(), draft.permissionCodes(),
                draft.customOrgUnitIds().isEmpty() ? null : draft.customOrgUnits());
    }
}
