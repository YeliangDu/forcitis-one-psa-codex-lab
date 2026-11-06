package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.kernel.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An emergency session has nothing but the administrator mode, so leaving it ends the session. */
@Service
public class ExitAdministratorMode {

    private static final Logger log = LoggerFactory.getLogger(ExitAdministratorMode.class);

    private final CurrentUser currentUser;
    private final SessionContext session;
    private final SessionRegistry sessions;
    private final AuditLog audit;
    private final ClientOrigin origin;

    ExitAdministratorMode(CurrentUser currentUser, SessionContext session, SessionRegistry sessions, AuditLog audit, ClientOrigin origin) {
        this.currentUser = currentUser;
        this.session = session;
        this.sessions = sessions;
        this.audit = audit;
        this.origin = origin;
    }

    @Transactional
    public void execute() {
        UserId userId = currentUser.userId();
        if (session.mode() != SessionMode.ADMINISTRATOR) {
            return;
        }
        LoginMethod method = session.loginMethod();
        audit.record(new AuditEntry("platform.identity.administrator-mode.exited", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), null)
                .withTrace(origin.address(), AuditOutcome.SUCCEEDED, method.auditKind()));
        log.error("ALERT platform.identity administrator mode left by user {}", userId.value());
        if (method == LoginMethod.EMERGENCY) {
            sessions.end();
        } else {
            sessions.endAdministratorMode();
        }
    }
}
