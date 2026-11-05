package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.integration.api.WecomEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A super_admin holder proves who they are again with a fresh WeCom code, then the same session gets the administrator mode for at
 * most one hour. It is entered anew each time and never extended; every entry is audited and alerts. There is no transaction around
 * the whole use case on purpose: the OAuth state is used up for good even when the entry is refused.
 */
@Service
public class EnterAdministratorMode {

    private static final Logger log = LoggerFactory.getLogger(EnterAdministratorMode.class);

    private final CurrentUser currentUser;
    private final SessionContext session;
    private final SessionRegistry sessions;
    private final WecomIdentification identification;
    private final AccountRepository accounts;
    private final AuditLog audit;
    private final ClientOrigin origin;
    private final MeterRegistry meters;
    private final TransactionTemplate transaction;

    EnterAdministratorMode(CurrentUser currentUser, SessionContext session, SessionRegistry sessions, WecomIdentification identification,
            AccountRepository accounts, AuditLog audit, ClientOrigin origin, MeterRegistry meters,
            PlatformTransactionManager transactions) {
        this.transaction = new TransactionTemplate(transactions);
        this.currentUser = currentUser;
        this.session = session;
        this.sessions = sessions;
        this.identification = identification;
        this.accounts = accounts;
        this.audit = audit;
        this.origin = origin;
        this.meters = meters;
    }

    public Instant execute(WecomLoginCommand command) {
        UserId userId = currentUser.userId();
        if (session.mode() == SessionMode.ADMINISTRATOR) {
            throw refused(userId, "already in the administrator mode, which is never extended");
        }
        WecomEntry entry = command.entry();
        if (entry == null) {
            throw refused(userId, "no entry");
        }
        switch (identification.identify(command, entry)) {
            case WecomIdentification.Outcome.Failed failed -> throw refused(userId, failed.reason());
            case WecomIdentification.Outcome.Identified identified -> {
                if (!identified.employee().employeeId().equals(currentUser.employeeId())) {
                    throw refused(userId, "the code proves another person");
                }
            }
        }
        if (!AccessProfile.of(accounts.findEnabledRoles(userId)).isAdministrator()) {
            throw refused(userId, "super_admin is not held");
        }
        Instant until = sessions.beginAdministratorMode();
        transaction.executeWithoutResult(status -> audit.record(new AuditEntry("platform.identity.administrator-mode.entered", userId, null,
                AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), "until " + until).withTrace(origin.address(), AuditOutcome.SUCCEEDED, session.loginMethod().auditKind())));
        meters.counter("forcitis.platform.identity.administrator.mode.entries").increment();
        log.error("ALERT platform.identity administrator mode entered by user {}, until {}", userId.value(), until);
        return until;
    }

    private DomainException refused(UserId userId, String reason) {
        audit.recordIndependently(new AuditEntry("platform.identity.administrator-mode.refused", userId, null,
                AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), reason).withTrace(origin.address(), AuditOutcome.FAILED, session.loginMethod().auditKind()));
        return new DomainException(IdentityError.PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED, ErrorKind.FORBIDDEN,
                "the administrator mode cannot be entered");
    }
}
