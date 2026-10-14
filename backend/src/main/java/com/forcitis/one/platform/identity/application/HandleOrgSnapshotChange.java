package com.forcitis.one.platform.identity.application;

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
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * A separated person loses every role, the emergency account bound to them and every session (ADR-0027 section 1; RULE-363). The
 * per-request check (SessionPrincipals) holds even if this event is late or lost, so session revocation does not depend on it.
 */
@Service
public class HandleOrgSnapshotChange {

    private static final Logger log = LoggerFactory.getLogger(HandleOrgSnapshotChange.class);
    private static final String ACTOR = "platform.identity.separation";

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final AccountRepository accounts;
    private final EmergencyAdminRepository emergencyAdmins;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final AuditLog audit;
    private final DomainEvents events;

    HandleOrgSnapshotChange(OrgDirectory directory, AccountProvisioning provisioning, AccountRepository accounts,
            EmergencyAdminRepository emergencyAdmins, SessionRegistry sessions, AccessCache cache, AuditLog audit, DomainEvents events) {
        this.directory = directory;
        this.provisioning = provisioning;
        this.accounts = accounts;
        this.emergencyAdmins = emergencyAdmins;
        this.sessions = sessions;
        this.cache = cache;
        this.audit = audit;
        this.events = events;
    }

    @ApplicationModuleListener
    public void on(OrgSnapshotChanged event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                ACTOR)) {
            for (UserId separated : event.separatedEmployees()) {
                separate(separated, "separated in the organisation snapshot");
            }
            for (UserId changed : event.changedEmployees()) {
                Optional<EmployeeProfile> employee = directory.findEmployee(changed);
                if (employee.isEmpty()) {
                    continue;
                }
                if (employee.get().employmentStatus() == EmploymentStatus.EMPLOYED) {
                    provisioning.ensureAccount(employee.get());
                    cache.evict(changed);
                } else {
                    separate(changed, "no longer employed");
                }
            }
        }
    }

    private void separate(UserId userId, String reason) {
        revokeRoles(userId);
        disableEmergencyAdmin(userId);
        sessions.revokeAll(userId);
        cache.evict(userId);
        audit.record(new AuditEntry("platform.identity.session.revoked", null, null, "user-account:" + userId.value(), reason)
                .withTrace(null, AuditOutcome.SUCCEEDED, null));
    }

    private void revokeRoles(UserId userId) {
        Optional<UserAccount> found = accounts.find(userId);
        if (found.isEmpty() || found.get().roles().isEmpty()) {
            return;
        }
        UserAccount account = found.get();
        Set<RoleCode> held = accounts.findAssignedRoleCodes(userId);
        account.revokeAllRoles();
        accounts.save(account);
        events.publish(account);
        held.stream().map(RoleCode::value).sorted(Comparator.naturalOrder()).forEach(code -> audit.record(new AuditEntry(
                "platform.identity.account.role.revoked", null, null, "user-account:" + userId.value(), "role=" + code + " cause=separation")));
    }

    private void disableEmergencyAdmin(UserId userId) {
        Optional<EmergencyAdmin> found = emergencyAdmins.findByUserId(userId);
        if (found.isEmpty() || !found.get().enabled()) {
            return;
        }
        EmergencyAdmin admin = found.get();
        admin.disable(EmergencyAdminDisableCause.SEPARATION);
        emergencyAdmins.save(admin);
        events.publish(admin);
        log.error("ALERT platform.identity the emergency account bound to separated user {} was disabled; check that at least two usable ones remain",
                userId.value());
    }
}
