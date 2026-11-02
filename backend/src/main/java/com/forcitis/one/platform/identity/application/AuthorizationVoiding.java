package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A confirmed request is applied only if what it was made against is still there (ADR-0034 decision 2 item 5): the account version, and every related
 * role still on the version it had. Otherwise it ends VOIDED and nothing is applied; the person asks again from the current state.
 */
@Component
class AuthorizationVoiding {

    private final RoleRepository roles;
    private final AuditLog audit;

    AuthorizationVoiding(RoleRepository roles, AuditLog audit) {
        this.roles = roles;
        this.audit = audit;
    }

    /** accountVersion is the version of the account read under its row lock; null where the request has no account. */
    boolean moved(AuthorizationRequest request, @Nullable Long accountVersion) {
        AuthorizationBase base = request.base();
        if (base.accountVersion() != null && !base.accountVersion().equals(accountVersion)) {
            return true;
        }
        return !roles.currentVersions(base.roleVersions()).containsAll(base.roleVersions());
    }

    /** Ends the request VOIDED and writes the audit entry; the caller saves and publishes the request. */
    void voidRequest(AuthorizationRequest request) {
        request.markVoided();
        audit.record(new AuditEntry("platform.identity.authorization.voided", null, null,
                AuditSubject.of(AuditSubjectKind.AUTHORIZATION_REQUEST, request.requestId().value()), "the base of the request moved after it was made; nothing was applied"));
    }
}
