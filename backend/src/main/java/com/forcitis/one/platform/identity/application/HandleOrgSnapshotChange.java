package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
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
import org.springframework.stereotype.Service;

/**
 * A separated person loses every role and every session (ADR-0027 section 1; RULE-363); the emergency account follows from the
 * AccountSeparated fact in its own transaction. The per-request check (SessionPrincipals) holds even if this event is late or lost,
 * so session revocation does not depend on it.
 */
@SystemActorName("platform.identity.separation")
@Service
public class HandleOrgSnapshotChange {

    private static final String ACTOR = HandleOrgSnapshotChange.class.getAnnotation(SystemActorName.class).value();
    private static final int MAX_ATTEMPTS = 3;

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final AuditLog audit;
    private final DomainEvents events;
    private final RevokeBrowserIdentifiers revokeBrowserIdentifiers;
    private final AccountEmployees accountEmployees;

    HandleOrgSnapshotChange(OrgDirectory directory, AccountProvisioning provisioning, AccountRepository accounts,
            SessionRegistry sessions, AccessCache cache, AuditLog audit, DomainEvents events,
            RevokeBrowserIdentifiers revokeBrowserIdentifiers, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.revokeBrowserIdentifiers = revokeBrowserIdentifiers;
        this.directory = directory;
        this.provisioning = provisioning;
        this.accounts = accounts;
        this.sessions = sessions;
        this.cache = cache;
        this.audit = audit;
        this.events = events;
    }

    @ApplicationModuleListener
    public void on(OrgSnapshotChanged event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                ACTOR)) {
            for (EmployeeId separated : event.separatedEmployees()) {
                accountEmployees.accountOf(separated).ifPresent(account -> separate(account, "separated in the organisation snapshot"));
            }
            for (EmployeeId changed : event.changedEmployees()) {
                Optional<EmployeeProfile> employee = directory.findEmployee(changed);
                if (employee.isEmpty()) {
                    continue;
                }
                if (employee.get().employmentStatus() == EmploymentStatus.EMPLOYED) {
                    provisioning.ensureAccount(employee.get()).ifPresent(cache::evict);
                } else {
                    accountEmployees.accountOf(changed).ifPresent(account -> separate(account, "no longer employed"));
                }
            }
        }
    }

    /** Also reached from HandleEmployeeSeparated, which enters the same actor scope. */
    void separate(UserId userId, String reason) {
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
