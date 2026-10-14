package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.delivery.execution.api.WorkPackageQueries;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One use case, one public method, one transaction. The submitter comes from the session, never from the request. */
@Service
public class SubmitTimesheet {

    private final WorkPackageQueries workPackages;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    SubmitTimesheet(WorkPackageQueries workPackages, ApprovalProcesses approvals, CurrentUser currentUser,
            ApplicationEventPublisher events) {
        this.workPackages = workPackages;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.events = events;
    }

    @Transactional
    public SubmissionResult execute(SubmitTimesheetCommand command) {
        Optional<Hours> remaining = workPackages.remainingHours(command.workPackageId());
        if (remaining.isEmpty()) {
            return new SubmissionResult.UnknownWorkPackage();
        }
        if (command.hours().exceeds(remaining.get())) {
            return new SubmissionResult.ExceedsRemainingHours(remaining.get().value());
        }
        UserId employee = currentUser.userId();
        ApprovalInstanceId instanceId = approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL,
                "timesheet:" + command.timesheetId().value(), employee, Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName())));
        events.publishEvent(new TimesheetSubmitted(command.timesheetId(), employee, instanceId));
        return new SubmissionResult.Submitted(instanceId);
    }
}
