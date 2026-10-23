package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.ConsumedEvents;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.event.DomainEvents;
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
@SystemActorName("cost.timesheet.approval-result")
@Component
class ApplyApprovalResult {

    private static final Logger log = LoggerFactory.getLogger(ApplyApprovalResult.class);
    private static final String CONSUMER = "cost.timesheet.apply-approval-result";

    private final ConsumedEvents consumed;
    private final TimesheetRepository timesheets;
    private final DomainEvents events;
    private final MeterRegistry meters;

    ApplyApprovalResult(ConsumedEvents consumed, TimesheetRepository timesheets, DomainEvents events, MeterRegistry meters) {
        this.consumed = consumed;
        this.timesheets = timesheets;
        this.events = events;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void on(ApprovalCompleted event) {
        apply(event);
    }

    void apply(ApprovalCompleted event) {
        if (!TimesheetApprovalProcess.KEY.equals(event.processKey())) {
            return;
        }
        if (!consumed.firstTime(event.envelope().eventId(), CONSUMER)) {
            return;
        }
        Optional<Timesheet> found = timesheetOf(event);
        if (found.isEmpty()) {
            log.warn("The approval result {} names timesheet {}, which does not exist", event.envelope().eventId(),
                    event.document().documentId().value());
            meters.counter("forcitis.cost.timesheet.approval.result.unknown").increment();
            return;
        }
        Timesheet timesheet = found.get();
        if (!timesheet.isAwaitingResultOfRound(event.round().value())) {
            log.info("Timesheet {} is not waiting for the result of round {}: ignored", event.document().documentId().value(), event.round().value());
            return;
        }
        switch (event.outcome()) {
            case APPROVED -> timesheet.approve();
            case REJECTED -> timesheet.reject(event.reason());
            case RETURNED -> timesheet.returnForRevision(event.reason());
            case CANCELLED -> timesheet.cancelSubmission(event.reason());
        }
        timesheets.save(timesheet);
        events.publish(timesheet);
    }

    private Optional<Timesheet> timesheetOf(ApprovalCompleted event) {
        return timesheets.findForUpdate(new TimesheetId(event.document().documentId().value()));
    }
}
