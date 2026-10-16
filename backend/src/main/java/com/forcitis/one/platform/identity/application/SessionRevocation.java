package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import org.springframework.stereotype.Component;

/** Ending every session of a person always leaves an audit entry with the reason; must run inside the caller's transaction. */
@Component
class SessionRevocation {
    private final SessionRegistry sessions;
    private final AuditLog audit;

    SessionRevocation(SessionRegistry sessions, AuditLog audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    void revokeAll(UserId userId, String reason) {
        sessions.revokeAll(userId);
        audit.record(new AuditEntry("platform.identity.session.revoked", null, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), reason));
    }
}
