package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Submitting a timesheet only publishes the fact; the approval of it starts here, in a transaction of its own that changes the approval
 * request and nothing else. Delivery is at least once and the approval starts once per (document, round), so a redelivery returns the
 * instance that exists. A start that fails is logged as an error, counted and left in the event registry for redelivery: the timesheet
 * stays SUBMITTED and nobody is told it is being approved when it is not.
 */
@Component
class StartApprovalOnSubmission {

    private static final Logger log = LoggerFactory.getLogger(StartApprovalOnSubmission.class);

    private final ApprovalProcesses approvals;
    private final MeterRegistry meters;

    StartApprovalOnSubmission(ApprovalProcesses approvals, MeterRegistry meters) {
        this.approvals = approvals;
        this.meters = meters;
    }

    @ApplicationModuleListener
    void on(TimesheetSubmitted event) {
        Set<String> flags = event.routingFlags().stream().map(TimesheetRoutingFlag::flagName).collect(Collectors.toSet());
        ApprovalStartRequest request = new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, event.document(),
                event.envelope().legalEntityId(), event.employee(), event.round(), event.envelope().eventId(), flags,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(String.valueOf(event.projectManager().value())))), event.contentHash(), event.summary());
        try {
            approvals.start(request);
        } catch (RuntimeException failed) {
            log.error("ALERT the approval of timesheet {} round {} could not start: {}", event.document().documentId().value(),
                    event.round().value(), failed.toString());
            meters.counter("forcitis.cost.timesheet.approval.start.failed").increment();
            throw failed;
        }
    }
}
