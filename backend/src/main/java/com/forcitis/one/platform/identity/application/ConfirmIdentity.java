package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person says the account a ticket logged them into is theirs; until then the session holds no permission at all. */
@Service
public class ConfirmIdentity {

    private final CurrentUser currentUser;
    private final SessionRegistry sessions;
    private final AuditLog audit;

    ConfirmIdentity(CurrentUser currentUser, SessionRegistry sessions, AuditLog audit) {
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public void execute() {
        var userId = currentUser.userId();
        sessions.confirmIdentity();
        audit.record(new AuditEntry("platform.identity.session.identity-confirmed", userId, null, "user-account:" + userId.value(),
                "the person confirmed the account of the ticket login"));
    }
}
