package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AdministratorMode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Never caches itself (infrastructure caches for at most 60 s); anything unknown is ineligible (fail-closed). An ordinary session
 * needs the person to be employed; an administrator mode or emergency session needs the account to be enabled, the emergency
 * account to be enabled (emergency sessions) and super_admin to be held right now, and does not look at employment (ADR-0027 section 1).
 */
@Service
public class SessionPrincipals {

    private final AccountRepository accounts;
    private final OrgDirectory directory;
    private final EmergencyAdminRepository emergencyAdmins;

    SessionPrincipals(AccountRepository accounts, OrgDirectory directory, EmergencyAdminRepository emergencyAdmins) {
        this.accounts = accounts;
        this.directory = directory;
        this.emergencyAdmins = emergencyAdmins;
    }

    @Transactional(readOnly = true)
    public SessionPrincipal resolve(UserId userId, SessionKind kind) {
        Optional<UserAccount> account = accounts.find(userId);
        if (account.isEmpty()) {
            return refused(Eligibility.ACCOUNT_MISSING);
        }
        if (!account.get().isEnabled()) {
            return refused(Eligibility.ACCOUNT_DISABLED);
        }
        AccessProfile profile = AccessProfile.of(accounts.findEnabledRoles(userId));
        if (kind.mode() == SessionMode.ADMINISTRATOR) {
            return administrator(userId, kind, profile);
        }
        Optional<EmployeeProfile> employee = directory.findEmployee(account.get().employeeId());
        if (employee.isEmpty()) {
            return refused(Eligibility.EMPLOYEE_UNKNOWN);
        }
        if (employee.get().employmentStatus() != EmploymentStatus.EMPLOYED) {
            return refused(Eligibility.EMPLOYEE_SEPARATED);
        }
        return new SessionPrincipal(Eligibility.ACTIVE, profile.permissionCodes().stream()
                .filter(code -> !AdministratorMode.PLATFORM_ONLY.contains(code)).collect(Collectors.toUnmodifiableSet()));
    }

    private SessionPrincipal administrator(UserId userId, SessionKind kind, AccessProfile profile) {
        if (kind.emergency() && !emergencyAdmins.findByUserId(userId).map(EmergencyAdmin::enabled).orElse(false)) {
            return refused(Eligibility.EMERGENCY_ADMIN_DISABLED);
        }
        if (!profile.isAdministrator()) {
            return refused(Eligibility.NOT_ADMINISTRATOR);
        }
        return new SessionPrincipal(Eligibility.ACTIVE, profile.administratorModePermissionCodes());
    }

    private static SessionPrincipal refused(Eligibility eligibility) {
        return new SessionPrincipal(eligibility, Set.of());
    }
}
