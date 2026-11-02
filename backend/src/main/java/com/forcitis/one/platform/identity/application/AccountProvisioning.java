package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The default role is granted when the account is opened and, once, when a reinstatement takes effect (HandleEmployeeReinstated), so an administrator's later removal is not overridden by a repeated delivery. */
@Component
@SystemActorName("platform.identity.account-provisioning")
class AccountProvisioning {

    private static final String ACTOR = AccountProvisioning.class.getAnnotation(SystemActorName.class).value();

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final DomainEvents events;
    private final String defaultRole;
    private final IdGenerator ids;
    private final OrgDirectory directory;
    private final AccountGrants grants;

    AccountProvisioning(AccountRepository accounts, RoleRepository roles, DomainEvents events, IdGenerator ids, OrgDirectory directory, AccountGrants grants,
            @Value("${forcitis.platform.identity.default-role}") String defaultRole) {
        this.grants = grants;
        this.ids = ids;
        this.directory = directory;
        this.accounts = accounts;
        this.roles = roles;
        this.events = events;
        this.defaultRole = defaultRole;
    }

    /** For an operations command naming an employee (ADR-0033 section 3 item 6): the account they have, or one opened now if they are employed. */
    UserId accountForOperations(EmployeeId employeeId) {
        return existingOrOpened(employeeId).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND, ErrorKind.NOT_FOUND,
                "employee " + employeeId.value() + " has no account and is not an employed person who could get one"));
    }

    Optional<UserId> existingOrOpened(EmployeeId employeeId) {
        return accounts.findByEmployee(employeeId).or(() -> directory.findEmployee(employeeId).flatMap(this::ensureAccount));
    }

    /** The account of an employed person, opened if they have none; empty for anyone not employed. */
    Optional<UserId> ensureAccount(EmployeeProfile employee) {
        if (employee.employmentStatus() != EmploymentStatus.EMPLOYED) {
            return Optional.empty();
        }
        Optional<UserId> existing = accounts.findByEmployee(employee.employeeId());
        if (existing.isPresent()) {
            return existing;
        }
        RoleId initial = roles.findByCode(new RoleCode(defaultRole)).orElseThrow(() -> new DomainException(
                IdentityError.PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING, ErrorKind.UNAVAILABLE, "the default role " + defaultRole + " does not exist"))
                .roleId();
        UserAccount opened = UserAccount.open(new UserId(ids.next()), employee.employeeId(), Set.of(initial));
        if (accounts.insertIfAbsent(opened)) {
            try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                    ACTOR)) {
                grants.replace(opened, Set.of(initial), AccountGrants.Origin.defaultRole());
                events.publish(opened);
            }
        }
        return Optional.of(accounts.findByEmployee(employee.employeeId()).orElseThrow(() -> new IllegalStateException(
                "no account of employee " + employee.employeeId().value() + " right after opening it")));
    }
}
