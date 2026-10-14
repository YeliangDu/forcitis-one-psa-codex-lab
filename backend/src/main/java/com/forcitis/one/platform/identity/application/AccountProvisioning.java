package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditTrail;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates the account of an employed person on first sight (sync or first login) and grants the configured default role
 * explicitly, once, with an audit entry (RULE-344). An administrator who later removes the role is not overridden: the
 * grant happens only for the call that actually created the account. Separated people get nothing.
 * Must run inside the caller's transaction.
 */
@Component
class AccountProvisioning {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final AuditTrail audit;
    private final String defaultRole;

    AccountProvisioning(AccountRepository accounts, RoleRepository roles, AuditTrail audit,
            @Value("${forcitis.platform.identity.default-role:employee}") String defaultRole) {
        this.accounts = accounts;
        this.roles = roles;
        this.audit = audit;
        this.defaultRole = defaultRole;
    }

    /** True when this call created the account. */
    boolean ensureAccount(EmployeeProfile employee) {
        if (employee.employmentStatus() != EmploymentStatus.EMPLOYED) {
            return false;
        }
        if (!accounts.createIfAbsent(employee.userId())) {
            return false;
        }
        String subject = "user-account:" + employee.userId().value();
        audit.record(new AuditEntry("platform.identity.account.created", null, null, subject, "created for an employed person"));
        Optional<Role> role = defaultRole.isBlank() ? Optional.empty() : roles.findByCode(new RoleCode(defaultRole));
        if (role.isPresent()) {
            accounts.grantRole(employee.userId(), role.get().roleId());
            audit.record(new AuditEntry("platform.identity.role.granted", null, null, subject,
                    "default role " + defaultRole + ", granted once at creation"));
        } else {
            audit.record(new AuditEntry("platform.identity.role.grant.skipped", null, null, subject,
                    "default role " + defaultRole + " does not exist"));
        }
        return true;
    }
}
