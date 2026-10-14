package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves who a session belongs to right now: account enabled, employee employed (RULE-326/327/338), and the permissions of
 * the enabled roles (RULE-343). The infrastructure caches the answer for at most 60 s and evicts on every change; this
 * class itself never caches. Anything unknown is ineligible (fail-closed).
 */
@Service
public class SessionPrincipals {

    private final AccountRepository accounts;
    private final OrgDirectory directory;

    SessionPrincipals(AccountRepository accounts, OrgDirectory directory) {
        this.accounts = accounts;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public SessionPrincipal resolve(UserId userId) {
        Optional<UserAccount> account = accounts.find(userId);
        if (account.isEmpty()) {
            return refused(Eligibility.ACCOUNT_MISSING);
        }
        if (!account.get().isEnabled()) {
            return refused(Eligibility.ACCOUNT_DISABLED);
        }
        Optional<EmployeeProfile> employee = directory.findEmployee(userId);
        if (employee.isEmpty()) {
            return refused(Eligibility.EMPLOYEE_UNKNOWN);
        }
        if (employee.get().employmentStatus() != EmploymentStatus.EMPLOYED) {
            return refused(Eligibility.EMPLOYEE_SEPARATED);
        }
        return new SessionPrincipal(Eligibility.ACTIVE, AccessProfile.of(accounts.findEnabledRoles(userId)).permissionCodes());
    }

    private static SessionPrincipal refused(Eligibility eligibility) {
        return new SessionPrincipal(eligibility, Set.of());
    }
}
