package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The local personnel command took effect (ADR-0068 item 2.5 C): the person loses every role, session and browser identifier (ADR-0027 section 1;
 * RULE-363); the emergency account follows from the AccountSeparated fact in its own transaction. Workforce judged the last-administrator floor
 * before publishing, so nothing is refused here; a person without an account is a no-op. The per-request check (SessionPrincipals) holds even if this event is late or lost,
 * so session revocation does not depend on it.
 */
@SystemActorName("platform.identity.separation")
@Component
class HandleEmployeeSeparated {

    private static final String ACTOR = HandleEmployeeSeparated.class.getAnnotation(SystemActorName.class).value();
    private static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final AuditLog audit;
    private final DomainEvents events;
    private final RevokeBrowserIdentifiers revokeBrowserIdentifiers;
    private final AccountEmployees accountEmployees;

    HandleEmployeeSeparated(AccountRepository accounts,
            SessionRegistry sessions, AccessCache cache, AuditLog audit, DomainEvents events,
            RevokeBrowserIdentifiers revokeBrowserIdentifiers, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.revokeBrowserIdentifiers = revokeBrowserIdentifiers;
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.audit = audit;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(EmployeeSeparated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(account -> separate(account, "separated by personnel command"));
        }
    }

    private void separate(UserId userId, String reason) {
        revokeRoles(userId);
        sessions.revokeAll(userId);
        revokeBrowserIdentifiers.execute(userId);
        cache.evict(userId);
        audit.record(new AuditEntry("platform.identity.session.revoked", null, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), reason)
                .withTrace(null, AuditOutcome.SUCCEEDED, null));
    }

    /** The account may be written by the listener that opens it at the same moment: the loser reads again instead of leaving the event undelivered. */
    private void revokeRoles(UserId userId) {
        for (int attempt = 1; ; attempt++) {
            Optional<UserAccount> found = accounts.find(userId);
            if (found.isEmpty() || found.get().roles().isEmpty()) {
                return;
            }
            UserAccount account = found.get();
            Set<RoleCode> held = accounts.findAssignedRoleCodes(userId);
            account.recordSeparation();
            try {
                accounts.save(account);
            } catch (DomainException conflict) {
                if (attempt >= MAX_ATTEMPTS || conflict.errorCode() != IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE) {
                    throw conflict;
                }
                continue;
            }
            events.publish(account);
            held.stream().map(RoleCode::value).sorted(Comparator.naturalOrder()).forEach(code -> audit.record(new AuditEntry(
                    "platform.identity.account.role.revoked", null, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()), "role=" + code + " cause=separation")));
            return;
        }
    }
}
