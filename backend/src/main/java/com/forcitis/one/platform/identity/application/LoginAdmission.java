package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.AccountOpeningRoute;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Failures are audited in their own transaction so they survive a rollback; must run inside the caller's transaction. */
@Component
class LoginAdmission {

    private static final Logger log = LoggerFactory.getLogger(LoginAdmission.class);

    private final AccountProvisioning provisioning;
    private final AccountRepository accounts;
    private final AuditLog audit;
    private final SessionRegistry sessions;
    private final ClientOrigin origin;
    private final Clock clock;
    private final MeterRegistry meters;
    private final BrowserIdentity browserIdentity;

    LoginAdmission(AccountProvisioning provisioning, AccountRepository accounts, AuditLog audit, SessionRegistry sessions,
            ClientOrigin origin, Clock clock, MeterRegistry meters, BrowserIdentity browserIdentity) {
        this.browserIdentity = browserIdentity;
        this.provisioning = provisioning;
        this.accounts = accounts;
        this.audit = audit;
        this.sessions = sessions;
        this.origin = origin;
        this.clock = clock;
        this.meters = meters;
    }

    /** Null for a person who has no account: they were never employed while signing in. */
    @Nullable UserId accountOf(EmployeeProfile employee) {
        return accounts.findByEmployee(employee.employeeId()).orElse(null);
    }

    Optional<IdentityError> check(EmployeeProfile employee) {
        if (employee.employmentStatus() != EmploymentStatus.EMPLOYED) {
            return Optional.of(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED);
        }
        AccountProvisioning.Provisioned provisioned = provisioning.ensureAccount(employee, AccountOpeningRoute.LOGIN)
                .orElseThrow(() -> new IllegalStateException("account vanished right after creation"));
        if (provisioned.opened()) {
            announceAccountOpenedAtLogin(provisioned.userId());
        }
        UserAccount account = accounts.find(provisioned.userId()).orElseThrow(() -> new IllegalStateException("account vanished right after creation"));
        return account.isEnabled() ? Optional.empty() : Optional.of(IdentityError.PLATFORM_IDENTITY_ACCOUNT_DISABLED);
    }

    LoginResult complete(UserId userId, LoginMethod method) {
        return complete(userId, method, true, null);
    }

    LoginResult completeFromTicket(UserId userId, LoginMethod method, @Nullable String presentedBrowserIdentifier) {
        BrowserIdentity.Judgement judgement = browserIdentity.judge(userId, method, presentedBrowserIdentifier);
        return complete(userId, method, !judgement.confirmationRequired(), judgement.newIdentifier());
    }

    private LoginResult complete(UserId userId, LoginMethod method, boolean identityConfirmed, @Nullable String browserIdentifier) {
        Instant now = clock.instant();
        accounts.recordLogin(userId, now, origin.address());
        AuditEntry succeeded = new AuditEntry("platform.identity.login.succeeded", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                describe(method, null));
        audit.record(succeeded.withTrace(origin.address(), AuditOutcome.SUCCEEDED, method.auditKind()));
        StartedSession started = sessions.start(userId, method, identityConfirmed);
        meters.counter("forcitis.platform.identity.login", "method", method.name(), "outcome", "success").increment();
        if (started.administratorModeExpiresAt() != null) {
            announceEmergencyLogin(userId, started.administratorModeExpiresAt());
        }
        return new LoginResult.Authenticated(userId, started.absoluteExpiresAt(), started.administratorModeExpiresAt(),
                !identityConfirmed, browserIdentifier);
    }

    /** An account opened by a login is rare and worth a look (ADR-0069 section 9): one audit entry and one ALERT line, no card; the hire and operations routes raise nothing. */
    private void announceAccountOpenedAtLogin(UserId userId) {
        audit.record(new AuditEntry("platform.identity.account.opened-at-login", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                "account opened by the login of an employed person; route " + AccountOpeningRoute.LOGIN));
        log.error("ALERT platform.identity account {} opened by a login, not by the hiring or an operations command", userId.value());
    }

    private void announceEmergencyLogin(UserId userId, Instant administratorModeExpiresAt) {
        audit.record(new AuditEntry("platform.identity.emergency.login", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                "administrator mode until " + administratorModeExpiresAt).withTrace(origin.address(), AuditOutcome.SUCCEEDED,
                LoginMethod.EMERGENCY.name()));
        meters.counter("forcitis.platform.identity.emergency.logins").increment();
        log.error("ALERT platform.identity emergency login by user {}; administrator mode until {}", userId.value(),
                administratorModeExpiresAt);
    }

    /** reason is for the audit trail only; it never reaches the caller. */
    LoginResult reject(IdentityError error, LoginMethod method, UserId actor, String reason) {
        AuditSubject subject = actor == null ? AuditSubject.without(AuditSubjectKind.LOGIN_ATTEMPT) : AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, actor.value());
        try {
            audit.recordIndependently(new AuditEntry("platform.identity.login.failed", actor, null, subject, describe(method, reason))
                    .withTrace(origin.address(), AuditOutcome.FAILED, method.auditKind()));
        } catch (RuntimeException failed) {
            log.error("Audit entry for the failed login of {} could not be written: {}", subject, failed.getClass().getName());
            meters.counter("forcitis.platform.identity.login.audit.failed").increment();
        }
        meters.counter("forcitis.platform.identity.login", "method", method.name(), "outcome", "failure").increment();
        return new LoginResult.Rejected(error);
    }

    private String describe(LoginMethod method, String reason) {
        return "method=" + method + (reason == null ? "" : " reason=" + reason);
    }
}
