package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The default role is granted only when the account is opened, so an administrator's later removal is not overridden. */
@Component
class AccountProvisioning {

    private static final String ACTOR = "platform.identity.account-provisioning";

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final DomainEvents events;
    private final String defaultRole;
    private final AccountEmployees accountEmployees;

    AccountProvisioning(AccountRepository accounts, RoleRepository roles, DomainEvents events,
            AccountEmployees accountEmployees, @Value("${forcitis.platform.identity.default-role}") String defaultRole) {
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.roles = roles;
        this.events = events;
        this.defaultRole = defaultRole;
    }

    boolean ensureAccount(EmployeeProfile employee) {
        if (employee.employmentStatus() != EmploymentStatus.EMPLOYED) {
            return false;
        }
        RoleId initial = roles.findByCode(new RoleCode(defaultRole)).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING, ErrorKind.UNAVAILABLE, "the default role " + defaultRole + " does not exist"))
                .roleId();
        UserAccount opened = UserAccount.open(accountEmployees.accountOf(employee.employeeId()), Set.of(initial));
        if (!accounts.insertIfAbsent(opened)) {
            return false;
        }
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                ACTOR)) {
            events.publish(opened);
        }
        return true;
    }
}
