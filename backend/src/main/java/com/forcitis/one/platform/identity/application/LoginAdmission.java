package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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

    LoginAdmission(AccountProvisioning provisioning, AccountRepository accounts, AuditLog audit, SessionRegistry sessions,
            ClientOrigin origin, Clock clock, MeterRegistry meters) {
        this.provisioning = provisioning;
        this.accounts = accounts;
        this.audit = audit;
        this.sessions = sessions;
        this.origin = origin;
        this.clock = clock;
        this.meters = meters;
    }

    Optional<IdentityError> check(EmployeeProfile employee) {
        if (employee.employmentStatus() != EmploymentStatus.EMPLOYED) {
            return Optional.of(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED);
        }
        provisioning.ensureAccount(employee);
        UserAccount account = accounts.find(employee.userId())
                .orElseThrow(() -> new IllegalStateException("account vanished right after creation"));
        return account.isEnabled() ? Optional.empty() : Optional.of(IdentityError.PLATFORM_IDENTITY_ACCOUNT_DISABLED);
    }

    LoginResult complete(UserId userId, LoginMethod method) {
        Instant now = clock.instant();
        accounts.recordLogin(userId, now, origin.address());
        audit.record(new AuditEntry("platform.identity.login.succeeded", userId, null, "user-account:" + userId.value(),
                describe(method, null)));
        Instant absoluteExpiresAt = sessions.start(userId, method);
        meters.counter("forcitis.platform.identity.login", "method", method.name(), "outcome", "success").increment();
        return new LoginResult.Authenticated(userId.value(), absoluteExpiresAt);
    }

    /** reason is for the audit trail only; it never reaches the caller. */
    LoginResult reject(IdentityError error, LoginMethod method, UserId actor, String reason) {
        String subject = actor == null ? "login:" + method.name() : "user-account:" + actor.value();
        try {
            audit.recordIndependently(new AuditEntry("platform.identity.login.failed", actor, null, subject, describe(method, reason)));
        } catch (RuntimeException failed) {
            log.error("Audit entry for the failed login of {} could not be written: {}", subject, failed.toString());
            meters.counter("forcitis.platform.identity.login.audit.failed").increment();
        }
        meters.counter("forcitis.platform.identity.login", "method", method.name(), "outcome", "failure").increment();
        return new LoginResult.Rejected(error);
    }

    private String describe(LoginMethod method, String reason) {
        String agent = origin.userAgent() == null ? "" : origin.userAgent().replaceAll("[\\p{Cntrl}]", " ");
        if (agent.length() > 200) {
            agent = agent.substring(0, 200);
        }
        return "method=" + method + " address=" + origin.address() + (reason == null ? "" : " reason=" + reason)
                + " agent=" + agent;
    }
}
