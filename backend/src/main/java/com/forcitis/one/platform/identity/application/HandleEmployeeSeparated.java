package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The local personnel command took effect (ADR-0068 item 2.5 C; ADR-0055 decision 3 item 2): in one transaction every open grant of the person is closed
 * (cause SEPARATION, the command of the event, an audit entry per grant written back), every session and browser identifier ends and the cache is
 * evicted; the emergency account follows in its own transaction. Workforce judged the last-administrator floor before publishing, so nothing is refused
 * here; a person without an account is a no-op, and a repeated delivery finds nothing left to close.
 */
@SystemActorName("platform.identity.separation")
@Component
class HandleEmployeeSeparated {

    private static final String ACTOR = HandleEmployeeSeparated.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final AccountRepository accounts;
    private final AccountGrants grants;
    private final DomainEvents events;
    private final SessionRevocation revocation;
    private final RevokeBrowserIdentifiers revokeBrowserIdentifiers;
    private final AccessCache cache;

    HandleEmployeeSeparated(AccountEmployees accountEmployees, AccountRepository accounts, AccountGrants grants, DomainEvents events, SessionRevocation revocation,
            RevokeBrowserIdentifiers revokeBrowserIdentifiers, AccessCache cache) {
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.grants = grants;
        this.events = events;
        this.revocation = revocation;
        this.revokeBrowserIdentifiers = revokeBrowserIdentifiers;
        this.cache = cache;
    }

    @ApplicationModuleListener
    void on(EmployeeSeparated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(account -> separate(account, event.personnelCommandId()));
        }
    }

    private void separate(UserId userId, PersonnelCommandId command) {
        accounts.findForUpdate(userId).ifPresent(account -> {
            if (!account.roles().isEmpty()) {
                grants.replace(account, Set.of(), AccountGrants.Origin.separation(command));
                account.recordSeparation();
                accounts.save(account);
                events.publish(account);
            }
        });
        revocation.revokeAll(userId, "separated by personnel command");
        revokeBrowserIdentifiers.execute(userId);
        cache.evict(userId);
    }
}
