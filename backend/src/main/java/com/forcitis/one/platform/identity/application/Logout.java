package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends the caller's session on the server (RULE-334) and records it. */
@Service
public class Logout {

    private final CurrentUser currentUser;
    private final SessionRegistry sessions;
    private final AuditLog audit;

    Logout(CurrentUser currentUser, SessionRegistry sessions, AuditLog audit) {
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public void execute() {
        UserId userId = currentUser.userId();
        sessions.end();
        audit.record(new AuditEntry("platform.identity.logout", userId, null, "user-account:" + userId.value(), "logout by the user"));
    }
}
