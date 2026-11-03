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

    /** Locks the related role rows: FOR SHARE for an account request, FOR UPDATE for a role change, which writes the role next (ADR-0034 decision 2 item 5). */
    void lockBase(AuthorizationRequest request) {
        AuthorizationBase base = request.base();
        roles.lockRolesOf(base.roleVersions(), base.accountVersion() == null);
    }

    /**
     * accountVersion is the version of the account read under its row lock; null where the request has no account. The related role rows are locked
     * before their versions are read ({@link #lockBase}).
     */
    boolean moved(AuthorizationRequest request, @Nullable Long accountVersion) {
        AuthorizationBase base = request.base();
        if (base.accountVersion() != null && !base.accountVersion().equals(accountVersion)) {
            return true;
        }
        lockBase(request);
        return !roles.currentVersions(base.roleVersions()).containsAll(base.roleVersions());
    }

    /** Ends the request VOIDED because its base moved and writes the audit entry; the caller saves and publishes the request. */
    void voidRequest(AuthorizationRequest request) {
        voidRequest(request, "the base of the request moved after it was made; nothing was applied");
    }

    /** Ends the request VOIDED with the audit entry saying why; the caller saves and publishes the request. */
    void voidRequest(AuthorizationRequest request, String why) {
        request.markVoided();
        audit.record(new AuditEntry("platform.identity.authorization.voided", null, null,
                AuditSubject.of(AuditSubjectKind.AUTHORIZATION_REQUEST, request.requestId().value()), why));
    }
}
