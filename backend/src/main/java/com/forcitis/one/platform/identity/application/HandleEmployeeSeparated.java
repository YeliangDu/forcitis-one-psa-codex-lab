package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The local personnel command took effect (ADR-0068 item 2.5 C): the person loses every role, session and browser identifier, as when a
 * snapshot shows them separated. Workforce judged the last-administrator floor before publishing, so nothing is refused here; a person
 * without an account is a no-op, and a repeated delivery finds nothing left to close.
 */
@SystemActorName("platform.identity.separation")
@Component
class HandleEmployeeSeparated {

    private final HandleOrgSnapshotChange separation;
    private final AccountEmployees accountEmployees;

    HandleEmployeeSeparated(HandleOrgSnapshotChange separation, AccountEmployees accountEmployees) {
        this.separation = separation;
        this.accountEmployees = accountEmployees;
    }

    @ApplicationModuleListener
    void on(EmployeeSeparated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null),
                "platform.identity.separation")) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(account -> separation.separate(account, "separated by personnel command"));
        }
    }
}
