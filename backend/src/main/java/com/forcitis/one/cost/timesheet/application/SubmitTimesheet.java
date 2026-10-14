package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.delivery.execution.api.WorkPackageQueries;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.identity.api.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The submitter and the legal entity come from the session, never from the request. A timesheet that is already in approval is
 * answered with its running instance; one that was approved or refused cannot be submitted again.
 */
@Service
public class SubmitTimesheet {

    private final WorkPackageQueries workPackages;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;
    private final TimesheetRepository repository;
    private final Timesheets timesheets;

    SubmitTimesheet(WorkPackageQueries workPackages, ApprovalProcesses approvals, CurrentUser currentUser, TimesheetRepository repository,
            Timesheets timesheets) {
        this.workPackages = workPackages;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.repository = repository;
        this.timesheets = timesheets;
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
        repository.lockForCreate(command.timesheetId());
        Optional<Timesheet> existing = repository.find(command.timesheetId());
        Timesheet timesheet;
        if (existing.isPresent()) {
            timesheet = existing.get();
            if (!timesheet.employee().equals(employee)) {
                throw new AccessDeniedException("Timesheet " + command.timesheetId().value() + " belongs to another employee");
            }
            if (timesheet.status() == TimesheetStatus.SUBMITTED) {
                return new SubmissionResult.Submitted(timesheet.approvalInstanceId().orElseThrow());
            }
            Transitions.run(() -> timesheet.revise(employee, command.workPackageId(), timesheets.today(), command.hours(),
                    command.projectManager()));
        } else {
            timesheet = Timesheet.draft(command.timesheetId(), employee, currentUser.legalEntityId(), command.workPackageId(),
                    timesheets.today(), command.hours(), command.projectManager());
        }
        ApprovalInstanceId instanceId = approvals.start(new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, timesheet.document(),
                timesheet.legalEntityId(), employee, Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(String.valueOf(timesheet.projectManager().value())))), timesheet.contentDigest()));
        Transitions.run(() -> timesheet.submit(employee, instanceId));
        timesheets.save(timesheet);
        return new SubmissionResult.Submitted(instanceId);
    }
}
