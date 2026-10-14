package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.delivery.execution.api.WorkPackageQueries;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The submitter and the legal entity come from the session, never from the request. Submitting changes this timesheet only; the
 * approval starts when the event it publishes is handled. Hours already submitted on the work package count as taken, and the
 * submissions on one work package are serialised, so two of them cannot both pass the check and together exceed what is left.
 * A timesheet already in approval is answered as it is; one whose round has ended but whose result is not applied yet cannot be
 * submitted again; one that was approved or refused cannot be submitted at all.
 */
@Service
public class SubmitTimesheet {

    private final WorkPackageQueries workPackages;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;
    private final OrgDirectory org;
    private final DocumentNumbers numbers;
    private final TimesheetRepository repository;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    SubmitTimesheet(WorkPackageQueries workPackages, ApprovalProcesses approvals, CurrentUser currentUser, OrgDirectory org,
            DocumentNumbers numbers, TimesheetRepository repository, DomainEvents events, Clock clock, BusinessZone zone) {
        this.workPackages = workPackages;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.org = org;
        this.numbers = numbers;
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public TimesheetView execute(SubmitTimesheetCommand command) {
        UserId employee = currentUser.userId();
        LocalDate today = zone.today(clock);
        repository.lockWorkPackage(command.workPackageId());
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
            requireEnoughHours(command.workPackageId(), command.hours(), command);
            timesheet.revise(employee, command.workPackageId(), command.workDate(), command.hours(), command.projectManager());
        } else {
            requireEnoughHours(command.workPackageId(), command.hours(), command);
            OrgUnitId orgUnit = org.findEmployee(employee).map(profile -> profile.orgUnitId()).orElseThrow(
                    () -> TimesheetError.COST_TIMESHEET_EMPLOYEE_UNKNOWN.refusal("User " + employee.value() + " is not in the organisation"));
            DocumentNumber number = numbers.next(DocumentType.TIMESHEET, currentUser.legalEntityId(), today);
            timesheet = Timesheet.draft(command.timesheetId(), number, new AggregateReference("NumberingRule", DocumentType.TIMESHEET.name()),
                    employee, currentUser.legalEntityId(), orgUnit, command.workPackageId(), command.workDate(), command.hours(),
                    command.projectManager(), today);
        }
        timesheet.submit(employee, today);
        repository.save(timesheet);
        events.publish(timesheet);
        return view(timesheet, false);
    }

    private void requireEnoughHours(WorkPackageId workPackage, Hours wanted, SubmitTimesheetCommand command) {
        Hours remaining = workPackages.remainingHours(workPackage).orElseThrow(
                () -> TimesheetError.COST_TIMESHEET_UNKNOWN_WORK_PACKAGE.refusal("No work package " + workPackage.value()));
        BigDecimal available = remaining.value().subtract(repository.pendingHours(workPackage, command.timesheetId()).value());
        if (wanted.value().compareTo(available) > 0) {
            throw TimesheetError.COST_TIMESHEET_EXCEEDS_REMAINING_HOURS.refusal("Work package " + workPackage.value() + " has "
                    + available.max(BigDecimal.ZERO).toPlainString() + " hours left, " + wanted.value().toPlainString() + " were submitted");
        }
    }

    private void requireRoundStillRunning(Timesheet timesheet) {
        Optional<ApprovalProgress> progress = approvals.progress(ProcessKey.COST_TIMESHEET_APPROVAL, timesheet.document());
        if (progress.isPresent() && progress.get().round().value() == timesheet.round() && progress.get().status() != ApprovalStatus.RUNNING) {
            throw TimesheetError.COST_TIMESHEET_RESULT_PENDING.refusal("The approval of round " + timesheet.round() + " ended as "
                    + progress.get().status() + " and the timesheet has not taken that over yet");
        }
    }

    static void requireExpectedVersion(Timesheet timesheet, java.util.OptionalLong expected) {
        if (expected.isPresent() && expected.getAsLong() != timesheet.version()) {
            throw TimesheetError.COST_TIMESHEET_CONCURRENT_CHANGE.refusal("Timesheet " + timesheet.timesheetId().value()
                    + " changed since it was read (version " + timesheet.version() + ", expected " + expected.getAsLong() + ")");
        }
    }

    private static void requireOwner(Timesheet timesheet, UserId employee) {
        if (!timesheet.employee().equals(employee)) {
            throw TimesheetError.COST_TIMESHEET_NOT_OWNER.refusal("Timesheet " + timesheet.timesheetId().value() + " belongs to another employee");
        }
    }

    private static TimesheetView view(Timesheet timesheet, boolean canWithdraw) {
        return new TimesheetView(timesheet.timesheetId(), timesheet.number(), timesheet.status(), timesheet.round(), timesheet.workDate(),
                timesheet.periodOfWork(), canWithdraw, timesheet.version());
    }
}
