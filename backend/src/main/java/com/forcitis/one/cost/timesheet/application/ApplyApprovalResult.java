package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.ConsumedEvents;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The approval owns the process, the timesheet owns its status, and this is the only place they meet. Delivery is at least once: an
 * event id is handled once, and a result for a round the timesheet is not waiting for (already applied, or superseded) changes nothing.
 */
@Component
class ApplyApprovalResult {

    private static final Logger log = LoggerFactory.getLogger(ApplyApprovalResult.class);
    private static final String CONSUMER = "cost.timesheet.apply-approval-result";

    private final ConsumedEvents consumed;
    private final Timesheets timesheets;

    ApplyApprovalResult(ConsumedEvents consumed, Timesheets timesheets) {
        this.consumed = consumed;
        this.timesheets = timesheets;
    }

    @ApplicationModuleListener
    void on(ApprovalCompleted event) {
        if (event.processKey() != ProcessKey.COST_TIMESHEET_APPROVAL) {
            return;
        }
        if (!consumed.firstTime(event.envelope().eventId(), CONSUMER)) {
            return;
        }
        Timesheet timesheet = timesheets.require(new TimesheetId(Long.parseLong(event.document().documentId().value())));
        if (!timesheet.isAwaitingResultOfRound(event.round())) {
            log.info("Timesheet {} is not waiting for the result of round {}: ignored", event.document().documentId().value(), event.round());
            return;
        }
        Transitions.run(() -> {
            switch (event.outcome()) {
                case APPROVED -> timesheet.approve();
                case REJECTED -> timesheet.reject(event.reason());
                case RETURNED -> timesheet.returnForRevision(event.reason());
                case CANCELLED -> timesheet.cancelSubmission(event.reason());
            }
        });
        timesheets.save(timesheet);
    }
}
