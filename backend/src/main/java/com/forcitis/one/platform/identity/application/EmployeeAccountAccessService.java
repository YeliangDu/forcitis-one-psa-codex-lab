package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The kernel face for workforce's WeCom user id change: it joins the caller's transaction, so the revocation commits or rolls back with the profile write. */
@Component
class EmployeeAccountAccessService implements EmployeeAccountAccess {
    private static final String EMPLOYEE_ROLE = "employee";

    private final AccountRepository accounts;
    private final SessionRegistry sessions;
    private final RevokeBrowserIdentifiers browserIdentifiers;
    private final AccessCache cache;

    EmployeeAccountAccessService(AccountRepository accounts, SessionRegistry sessions, RevokeBrowserIdentifiers browserIdentifiers, AccessCache cache) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.browserIdentifiers = browserIdentifiers;
        this.cache = cache;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean holdsOnlyEmployeeRole(EmployeeId employee) {
        return accounts.findByEmployee(employee).map(account -> accounts.findAssignedRoleCodes(account).stream().map(RoleCode::value).allMatch(EMPLOYEE_ROLE::equals))
                .orElse(true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeSessions(EmployeeId employee) {
        accounts.findByEmployee(employee).ifPresent(account -> {
            sessions.revokeAll(account);
            browserIdentifiers.execute(account);
            cache.evict(account);
        });
    }
}
