package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A correction that undoes a wrong separation (the person had no department before, has one today; ADR-0082 decision 2) raises one
 * sensitive-role review for the person if the account holds a sensitive role when the event is handled; never a scope review, never one per
 * role. A correction between two departments or positions of an employed person is handled like a transfer (ADR-0081 decision 3, ADR-0082
 * decision 1): one sensitive-role review per changed field, and one scope review when the department changed. A correction after which the
 * person is not employed, or one that changes neither field, raises nothing and is not retried. A repeated delivery, or another event of the
 * same command, adds nothing.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeCorrected {

    private static final String ACTOR = HandleEmployeeCorrected.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final OpenAssignmentReviews review;

    HandleEmployeeCorrected(AccountEmployees accountEmployees, OpenAssignmentReviews review) {
        this.accountEmployees = accountEmployees;
        this.review = review;
    }

    @ApplicationModuleListener
    void on(EmployeeCorrected event) {
        if (event.newOrgUnitId() == null || event.triggerFieldKinds().isEmpty()) {
            return;
        }
        boolean undoesSeparation = event.previousOrgUnitId() == null;
        if (undoesSeparation && !event.triggerFieldKinds().contains(TriggerFieldKind.ORG_UNIT)) {
            return;
        }
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> {
                if (undoesSeparation) {
                    review.openFor(user, event.envelope(), event.personnelCommandId(), TriggerFieldKind.ORG_UNIT, null, event.newOrgUnitId(), null,
                            event.newPositionId());
                    return;
                }
                for (TriggerFieldKind field : event.triggerFieldKinds()) {
                    review.openFor(user, event.envelope(), event.personnelCommandId(), field, event.previousOrgUnitId(), event.newOrgUnitId(),
                            event.previousPositionId(), event.newPositionId());
                    if (field == TriggerFieldKind.ORG_UNIT) {
                        review.openScopeReviewFor(user, event.employeeId(), event.envelope(), event.personnelCommandId(), event.previousOrgUnitId(), event.newOrgUnitId(),
                                event.previousPositionId(), event.newPositionId());
                    }
                }
            });
        }
    }
}
