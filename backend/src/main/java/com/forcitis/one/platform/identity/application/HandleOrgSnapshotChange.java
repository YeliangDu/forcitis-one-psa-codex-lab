package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditTrail;
import java.util.Optional;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Reacts to a committed organisation sync (RULE-338/344/363/366): separated people lose every session at once; people who
 * changed are looked up again - an employed person without an account gets one, with the default role granted once and
 * audited; a separated person is cut off and gets nothing. The per-request check of account and employment state
 * (SessionPrincipals) holds even if this event is late or lost, so revocation does not depend on it.
 */
@Service
public class HandleOrgSnapshotChange {

    private final OrgDirectory directory;
    private final AccountProvisioning provisioning;
    private final SessionRegistry sessions;
    private final AccessCache cache;
    private final AuditTrail audit;

    HandleOrgSnapshotChange(OrgDirectory directory, AccountProvisioning provisioning, SessionRegistry sessions, AccessCache cache,
            AuditTrail audit) {
        this.directory = directory;
        this.provisioning = provisioning;
        this.sessions = sessions;
        this.cache = cache;
        this.audit = audit;
    }

    @ApplicationModuleListener
    public void on(OrgSnapshotChanged event) {
        for (UserId separated : event.separatedEmployees()) {
            revoke(separated, "separated in the organisation snapshot");
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
                revoke(changed, "no longer employed");
            }
        }
    }

    private void revoke(UserId userId, String reason) {
        sessions.revokeAll(userId);
        cache.evict(userId);
        audit.record(new AuditEntry("platform.identity.session.revoked", null, null, "user-account:" + userId.value(), reason));
    }
}
