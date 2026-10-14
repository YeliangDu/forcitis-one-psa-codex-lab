package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.ConsumedEvents;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
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
    private final MeterRegistry meters;

    ApplyApprovalResult(ConsumedEvents consumed, Timesheets timesheets, MeterRegistry meters) {
        this.consumed = consumed;
        this.timesheets = timesheets;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void on(ApprovalCompleted event) {
        apply(event);
    }

    void apply(ApprovalCompleted event) {
        if (event.processKey() != ProcessKey.COST_TIMESHEET_APPROVAL) {
            return;
        }
        if (!consumed.firstTime(event.envelope().eventId(), CONSUMER)) {
            return;
        }
        Optional<Timesheet> found = timesheetOf(event);
        if (found.isEmpty()) {
            log.warn("The approval result {} names timesheet {}, which does not exist", event.envelope().eventId(),
                    event.document().documentId().value());
            meters.counter("cost.timesheet.approval.result.unknown").increment();
            return;
        }
        Timesheet timesheet = found.get();
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

    private Optional<Timesheet> timesheetOf(ApprovalCompleted event) {
        try {
            return timesheets.find(new TimesheetId(Long.parseLong(event.document().documentId().value())));
        } catch (NumberFormatException notOurs) {
            return Optional.empty();
        }
    }
}
