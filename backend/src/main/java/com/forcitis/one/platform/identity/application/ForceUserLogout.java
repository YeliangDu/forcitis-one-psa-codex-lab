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
import com.forcitis.one.platform.identity.domain.Role;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One person at a time, with a reason, never oneself (ADR-0027 section 3); ending a super_admin or security auditor session alerts. */
@Service
public class ForceUserLogout {

    static final int MAX_REASON_LENGTH = 200;

    private static final Logger log = LoggerFactory.getLogger(ForceUserLogout.class);

    private final CurrentUser currentUser;
    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final AuditLog audit;
    private final MeterRegistry meters;
    private final ClientOrigin origin;

    ForceUserLogout(CurrentUser currentUser, AccountRepository accounts, SessionRegistry sessions, AccessCache cache, AuditLog audit,
            MeterRegistry meters, ClientOrigin origin) {
        this.origin = origin;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.audit = audit;
        this.meters = meters;
    }

    @Transactional
    public void execute(UserId target, String reason) {
        String cleaned = reason == null ? "" : reason.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_REASON_LENGTH) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_FORCED_LOGOUT_REASON_REQUIRED, ErrorKind.UNPROCESSABLE,
                    "a reason of 1.." + MAX_REASON_LENGTH + " characters is required");
        }
        UserId actor = currentUser.userId();
        if (target.equals(actor)) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_FORCED_LOGOUT_SELF_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "an administrator cannot end their own sessions this way; log out instead");
        }
        if (accounts.find(target).isEmpty()) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND, ErrorKind.NOT_FOUND, "no such account");
        }
        AccessProfile held = AccessProfile.of(accounts.findEnabledRoles(target));
        sessions.revokeAll(target);
        cache.evict(target);
        audit.record(new AuditEntry("platform.identity.session.revoked", actor, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, target.value()),
                "forced logout: " + cleaned).withTrace(origin.address(), AuditOutcome.SUCCEEDED, null));
        if (held.isAdministrator() || held.roleCodes().contains(Role.SECURITY_AUDITOR)) {
            meters.counter("forcitis.platform.identity.administrator.logouts").increment();
            log.error("ALERT platform.identity user {} ended the sessions of user {}, who holds {}: {}", actor.value(), target.value(),
                    held.isAdministrator() ? Role.SUPER_ADMIN.value() : Role.SECURITY_AUDITOR.value(), cleaned);
        }
    }
}
