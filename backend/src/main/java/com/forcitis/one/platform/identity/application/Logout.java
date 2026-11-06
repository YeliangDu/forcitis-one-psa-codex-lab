package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.kernel.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Logout {

    private final CurrentUser currentUser;
    private final SessionRegistry sessions;
    private final AuditLog audit;
    private final ClientOrigin origin;
    private final SessionContext session;

    Logout(CurrentUser currentUser, SessionRegistry sessions, AuditLog audit, ClientOrigin origin, SessionContext session) {
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.audit = audit;
        this.origin = origin;
        this.session = session;
    }

    @Transactional
    public void execute() {
        UserId userId = currentUser.userId();
        String method = session.loginMethod().auditKind();
        sessions.end();
        audit.record(new AuditEntry("platform.identity.logout", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), "logout by the user")
                .withTrace(origin.address(), AuditOutcome.SUCCEEDED, method));
    }
}
