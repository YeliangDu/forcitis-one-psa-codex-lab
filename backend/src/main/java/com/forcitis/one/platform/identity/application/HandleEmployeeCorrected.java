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
 * role. A correction after which the person is not employed raises nothing. A repeated delivery, or another event of the same command,
 * adds nothing. Corrections that move an employed person between departments are not handled here.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeCorrected {

    private static final String ACTOR = HandleEmployeeCorrected.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final OpenSensitiveRoleReview review;

    HandleEmployeeCorrected(AccountEmployees accountEmployees, OpenSensitiveRoleReview review) {
        this.accountEmployees = accountEmployees;
        this.review = review;
    }

    @ApplicationModuleListener
    void on(EmployeeCorrected event) {
        boolean undoesSeparation = event.triggerFieldKinds().contains(TriggerFieldKind.ORG_UNIT) && event.previousOrgUnitId() == null
                && event.newOrgUnitId() != null;
        if (!undoesSeparation) {
            return;
        }
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> review.openFor(user, event.envelope(), event.personnelCommandId(),
                    TriggerFieldKind.ORG_UNIT, null, event.newOrgUnitId(), null, event.newPositionId()));
        }
    }
}
