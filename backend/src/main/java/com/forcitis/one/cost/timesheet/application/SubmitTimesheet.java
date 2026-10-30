package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.cost.timesheet.api.TimesheetDocument;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The submitter and the legal entity come from the session, never from the request. Submitting changes this timesheet only; the
 * approval starts when the event it publishes is handled.
 * A timesheet already in approval is answered as it is; one whose round has ended but whose result is not applied yet cannot be
 * submitted again; one that was approved cannot be submitted at all, and one that was refused is changed and submitted again like a returned one.
 * Each employee has one timesheet per work date, whatever its status.
 */
@Service
public class SubmitTimesheet {

    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;
    private final OrgDirectory org;
    private final EmployeeLegalEntities legalEntities;
    private final DocumentNumbers numbers;
    private final TimesheetRepository repository;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    SubmitTimesheet(ApprovalProcesses approvals, CurrentUser currentUser, OrgDirectory org,
            EmployeeLegalEntities legalEntities, DocumentNumbers numbers, TimesheetRepository repository, DomainEvents events, Clock clock, BusinessZone zone) {
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.org = org;
        this.legalEntities = legalEntities;
        this.numbers = numbers;
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public TimesheetView execute(SubmitTimesheetCommand command) {
        EmployeeId employee = currentUser.employeeId();
        LocalDate today = zone.today(clock);
        repository.lockForCreate(command.timesheetId());
        Optional<Timesheet> existing = repository.findForUpdate(command.timesheetId());
        Timesheet timesheet;
        if (existing.isPresent()) {
            timesheet = existing.get();
            requireOwner(timesheet, employee);
            requireExpectedVersion(timesheet, command.expectedVersion());
            if (timesheet.status() == TimesheetStatus.SUBMITTED) {
                requireRoundStillRunning(timesheet);
                return view(timesheet, false);
            }
            requireEmployed(command.projectManager());
            requireNotOnLeave(employee, command.workDate());
            timesheet.revise(employee, command.workDate(), command.hours(), command.projectManager());
        } else {
            requireEmployed(command.projectManager());
            requireNotOnLeave(employee, command.workDate());
            OrgUnitId orgUnit = org.findEmployee(employee).map(profile -> profile.orgUnitId()).orElseThrow(
                    () -> TimesheetError.COST_TIMESHEET_EMPLOYEE_UNKNOWN.refusal("User " + employee.value() + " is not in the organisation"));
            LegalEntityId legalEntity = legalEntities.on(employee, command.workDate());
            if (repository.existsFor(employee, command.workDate())) {
                throw TimesheetError.COST_TIMESHEET_WORK_DATE_DUPLICATE.refusal("The employee already has a timesheet for " + command.workDate());
            }
            IssuedNumber issued = numbers.next(TimesheetDocument.TYPE, legalEntity, today);
            timesheet = Timesheet.draft(command.timesheetId(), issued.number(), new AggregateReference("NumberingCounter", issued.counterId().value()),
                    issued.ruleVersionId(), employee, legalEntity, orgUnit, command.workDate(), command.hours(),
                    command.projectManager(), today);
        }
        timesheet.submit(employee, today);
        repository.save(timesheet);
        events.publish(timesheet);
        return view(timesheet, false);
    }

    /** During unpaid leave a timesheet may be submitted only for days before the leave starts (user decision 2026-10-30, ADR-0068 section 3): no timesheet for a day that was a leave day. */
    private void requireNotOnLeave(EmployeeId employee, LocalDate workDate) {
        if (org.isOnUnpaidLeave(employee, workDate)) {
            throw TimesheetError.COST_TIMESHEET_ON_UNPAID_LEAVE.refusal("The employee is on unpaid leave on " + workDate + ", so no timesheet can be submitted for that day");
        }
    }

    private void requireEmployed(EmployeeId projectManager) {
        boolean employed = org.findEmployee(projectManager).filter(profile -> profile.employmentStatus() == EmploymentStatus.EMPLOYED).isPresent();
        if (!employed) {
            throw TimesheetError.COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN.refusal("User " + projectManager.value()
                    + " is not an employed member of the organisation, so cannot review the timesheet");
        }
    }

    private void requireRoundStillRunning(Timesheet timesheet) {
        Optional<ApprovalProgress> progress = approvals.progress(TimesheetApprovalProcess.KEY, timesheet.document());
        if (progress.isPresent() && progress.get().round().value() == timesheet.round() && progress.get().status() != ApprovalStatus.RUNNING) {
            throw TimesheetError.COST_TIMESHEET_RESULT_PENDING.refusal("The approval of round " + timesheet.round() + " ended as "
                    + progress.get().status() + " and the timesheet has not taken that over yet");
        }
    }

    static void requireExpectedVersion(Timesheet timesheet, java.util.OptionalLong expected) {
        if (expected.isPresent() && expected.getAsLong() != timesheet.version()) {
            throw KernelError.KERNEL_CONCURRENT_CHANGE.refusal("Timesheet " + timesheet.timesheetId().value()
                    + " changed since it was read (version " + timesheet.version() + ", expected " + expected.getAsLong() + ")");
        }
    }

    private static void requireOwner(Timesheet timesheet, EmployeeId employee) {
        if (!timesheet.employee().equals(employee)) {
            throw TimesheetError.COST_TIMESHEET_NOT_FOUND.refusal("No such timesheet");
        }
    }

    private static TimesheetView view(Timesheet timesheet, boolean canWithdraw) {
        return new TimesheetView(timesheet.timesheetId(), timesheet.number(), timesheet.status(), timesheet.round(), timesheet.workDate(),
                timesheet.periodOfWork(), canWithdraw, timesheet.version());
    }
}
