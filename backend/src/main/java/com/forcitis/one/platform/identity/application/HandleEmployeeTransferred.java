package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A transfer raises one sensitive-role review per changed field (department, position) for a person whose account holds a sensitive role when
 * the event is handled (ADR-0032 decision 5), and, when the department changed, one scope review for a person who holds a role scoped to custom departments. A transfer with no department before it cannot be a transfer (ADR-0081 decision 3, ADR-0082
 * decision 2): it is refused with one alert line and consumed, because a redelivery would be refused the same way.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeTransferred {

    private static final Logger LOG = LoggerFactory.getLogger(HandleEmployeeTransferred.class);
    private static final String ACTOR = HandleEmployeeTransferred.class.getAnnotation(SystemActorName.class).value();

    private final AccountEmployees accountEmployees;
    private final OpenAssignmentReviews review;

    HandleEmployeeTransferred(AccountEmployees accountEmployees, OpenAssignmentReviews review) {
        this.accountEmployees = accountEmployees;
        this.review = review;
    }

    @ApplicationModuleListener
    void on(EmployeeTransferred event) {
        if (event.previousOrgUnitId() == null) {
            LOG.error("ALERT P2 platform.identity event {} ({}) has no department before the transfer: refused, no review item raised",
                    event.envelope().eventId(), event.envelope().eventType());
            return;
        }
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> {
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
