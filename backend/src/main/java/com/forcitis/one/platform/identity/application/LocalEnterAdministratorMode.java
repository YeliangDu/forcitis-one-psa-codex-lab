package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Card #1499: a developer's own machine only (profile local; the bean does not exist in any other profile). EnterAdministratorMode without the WeCom proof, because
 * there is no WeCom here: the same separate, explicit step, for a session that was started by the local test login, whose account holds super_admin; the same
 * SessionRegistry start (one hour, never extended), the same audit entry and ALERT. An ordinary login never gets the mode, and a session of any other login method
 * cannot use this class.
 */
@Service
@Profile("local")
public class LocalEnterAdministratorMode {

    private static final Logger log = LoggerFactory.getLogger(LocalEnterAdministratorMode.class);

    private final CurrentUser currentUser;
    private final SessionContext session;
    private final SessionRegistry sessions;
    private final AccountRepository accounts;
    private final AuditLog audit;
    private final ClientOrigin origin;
    private final MeterRegistry meters;
    private final TransactionTemplate transaction;

    LocalEnterAdministratorMode(CurrentUser currentUser, SessionContext session, SessionRegistry sessions, AccountRepository accounts, AuditLog audit,
            ClientOrigin origin, MeterRegistry meters, PlatformTransactionManager transactions) {
        this.transaction = new TransactionTemplate(transactions);
        this.currentUser = currentUser;
        this.session = session;
        this.sessions = sessions;
        this.accounts = accounts;
        this.audit = audit;
        this.origin = origin;
        this.meters = meters;
    }

    public Instant execute() {
        UserId userId = currentUser.userId();
        if (session.mode() == SessionMode.ADMINISTRATOR) {
            throw refused(userId, "already in the administrator mode, which is never extended");
        }
        if (session.loginMethod() != LoginMethod.LOCAL_TEST) {
            throw refused(userId, "not a session of the local test login");
        }
        if (!AccessProfile.of(accounts.findEnabledRoles(userId)).isAdministrator()) {
            throw refused(userId, "super_admin is not held");
        }
        Instant until = sessions.beginAdministratorMode();
        transaction.executeWithoutResult(status -> audit.record(new AuditEntry("platform.identity.administrator-mode.entered", userId, null,
                AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), "until " + until + " (local test login, no WeCom proof)")
                .withTrace(origin.address(), AuditOutcome.SUCCEEDED, session.loginMethod().auditKind())));
        meters.counter("forcitis.platform.identity.administrator.mode.entries").increment();
        log.error("ALERT platform.identity administrator mode entered by user {}, until {} (local test login)", userId.value(), until);
        return until;
    }

    private DomainException refused(UserId userId, String reason) {
        audit.recordIndependently(new AuditEntry("platform.identity.administrator-mode.refused", userId, null,
                AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), reason).withTrace(origin.address(), AuditOutcome.FAILED, session.loginMethod().auditKind()));
        return new DomainException(IdentityError.PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED, ErrorKind.FORBIDDEN, "the administrator mode cannot be entered");
    }
}
