package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.HandledOutcome;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A reinstatement took effect (ADR-0034 section 3 item 5, ADR-0055 section 3 item 6; D-1a-17): when the directory shows the person employed and the
 * account holds no open grant, exactly one default-role grant is opened, as when an account is opened.
 * Earlier grants stay closed and are not restored. A person without an account gets none, and a repeated delivery opens nothing, also after an
 * administrator took the role away.
 */
@SystemActorName("platform.identity.account-provisioning")
@Component
class HandleEmployeeReinstated {

    private static final String ACTOR = HandleEmployeeReinstated.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final OrgDirectory directory;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final AccountGrants grants;
    private final DomainEvents events;
    private final AccessCache cache;
    private final String defaultRole;

    HandleEmployeeReinstated(AccountEmployees accountEmployees, OrgDirectory directory, AccountRepository accounts, RoleRepository roles, AccountGrants grants,
            DomainEvents events, AccessCache cache, @Value("${forcitis.platform.identity.default-role}") String defaultRole) {
        this.accountEmployees = accountEmployees;
        this.directory = directory;
        this.accounts = accounts;
        this.roles = roles;
        this.grants = grants;
        this.events = events;
        this.cache = cache;
        this.defaultRole = defaultRole;
    }

    @ApplicationModuleListener
    void on(EmployeeReinstated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> openDefaultRole(user, event));
        }
    }

    /**
     * The account row is locked first; then the record of the command is read, and only then the directory (a separation that takes effect meanwhile
     * either shows in the reading or waits for this transaction and closes the grant opened here). Every completed decision is recorded, so a redelivery,
     * also after an administrator removed the role or after a later separation, never decides again.
     */
    private void openDefaultRole(UserId user, EmployeeReinstated event) {
        accounts.findForUpdate(user).ifPresent(account -> {
            if (accounts.isHandled(event.personnelCommandId())) {
                return;
            }
            boolean employed = directory.findEmployee(event.employeeId()).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
            if (!employed) {
                accounts.saveHandled(event.personnelCommandId(), user, HandledOutcome.NOT_EMPLOYED, null);
                return;
            }
            boolean marked = account.separatedAt().isPresent();
            account.recordReinstatement();
            if (!account.roles().isEmpty()) {
                if (marked) {
                    accounts.save(account);
                    events.publish(account);
                }
                accounts.saveHandled(event.personnelCommandId(), user, HandledOutcome.ALREADY_GRANTED, null);
                return;
            }
            var initial = roles.findByCode(new RoleCode(defaultRole)).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING,
                    ErrorKind.UNAVAILABLE, "the default role " + defaultRole + " does not exist")).roleId();
            GrantChanges changes = grants.replace(account, Set.of(initial), AccountGrants.Origin.defaultRole());
            accounts.save(account);
            events.publish(account);
            accounts.saveHandled(event.personnelCommandId(), user, HandledOutcome.GRANT_OPENED, changes.opened().getFirst().id());
            cache.evict(user);
        });
    }
}
