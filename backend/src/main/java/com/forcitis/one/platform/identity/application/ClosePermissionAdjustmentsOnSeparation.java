package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The person was separated: every OPEN review item about them closes with the separation command (F-1 invariant 29), in a transaction of its own that
 * writes only the items, apart from the one that closes the grants. An UPHELD item stays; a person without an account has no item; a repeated delivery
 * finds nothing OPEN left.
 */
@SystemActorName("platform.identity.separation")
@Component
class ClosePermissionAdjustmentsOnSeparation {

    private static final String ACTOR = ClosePermissionAdjustmentsOnSeparation.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final PermissionAdjustmentRepository adjustments;
    private final DomainEvents events;
    private final Clock clock;

    ClosePermissionAdjustmentsOnSeparation(AccountEmployees accountEmployees, PermissionAdjustmentRepository adjustments, DomainEvents events, Clock clock) {
        this.accountEmployees = accountEmployees;
        this.adjustments = adjustments;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(EmployeeSeparated event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> close(user, event.personnelCommandId()));
        }
    }

    private void close(UserId user, PersonnelCommandId command) {
        Instant now = clock.instant();
        for (PermissionAdjustmentId id : adjustments.openOf(user)) {
            PermissionAdjustment item = adjustments.findById(id).orElseThrow();
            item.closeBySeparation(command, now);
            adjustments.update(item);
            events.publish(item);
        }
    }
}
