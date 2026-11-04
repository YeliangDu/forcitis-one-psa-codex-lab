package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.events.EmployeeUnpaidLeaveStarted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * An unpaid leave started: a person whose account holds a sensitive role when the event is handled gets one sensitive-role review (ADR-0068
 * decision 3). Nothing is granted or taken away; a person without an account, or without a sensitive role, gets none.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeUnpaidLeaveStarted {

    private static final String ACTOR = HandleEmployeeUnpaidLeaveStarted.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final OpenAssignmentReviews review;

    HandleEmployeeUnpaidLeaveStarted(AccountEmployees accountEmployees, OpenAssignmentReviews review) {
        this.accountEmployees = accountEmployees;
        this.review = review;
    }

    @ApplicationModuleListener
    void on(EmployeeUnpaidLeaveStarted event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> review.openForLeave(user, event.envelope(), event.personnelCommandId()));
        }
    }
}
