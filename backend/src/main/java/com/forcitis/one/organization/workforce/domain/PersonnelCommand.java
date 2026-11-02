package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A personnel command (ADR-0068 item 1): what to do to one employee from one business day on. The row is append-only; only the status, the
 * application time and the withdrawal columns ever change, and the status moves by {@link CommandStatus#MACHINE}. The target components are the ones the kind needs and
 * only those, the same shapes the table's CHECK constraints hold. The event sequence it carries is the employee's: a command that takes effect
 * publishes an event whose envelope is about the employee (item 1.9).
 */
public final class PersonnelCommand extends AggregateRoot {
    public static final int MAX_REASON_LENGTH = 500;

    private final PersonnelCommandId id;
    private final EmployeeId employeeId;
    private final PersonnelCommandKind kind;
    private final LocalDate effectiveDate;
    private final @Nullable OrgUnitId targetOrgUnitId;
    private final @Nullable PositionId targetPositionId;
    private final @Nullable EmployeeId targetReportsToEmployeeId;
    private final @Nullable Boolean isTargetFeeEarner;
    private final @Nullable EmploymentPhaseKind targetEmploymentPhaseKind;
    private final UserId createdBy;
    private final Instant createdAt;
    private CommandStatus status;
    private @Nullable Instant appliedAt;
    private @Nullable Instant withdrawnAt;
    private @Nullable UserId withdrawnBy;
    private @Nullable String withdrawalReason;

    private PersonnelCommand(PersonnelCommandId id, EmployeeId employeeId, PersonnelCommandKind kind, LocalDate effectiveDate, CommandStatus status,
                             @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, @Nullable EmployeeId targetReportsToEmployeeId,
                             @Nullable Boolean isTargetFeeEarner, @Nullable EmploymentPhaseKind targetEmploymentPhaseKind, UserId createdBy, Instant createdAt,
                             @Nullable Instant appliedAt, long version, long eventSequence) {
        super(version, eventSequence);
        this.id = Objects.requireNonNull(id, "id");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.effectiveDate = Objects.requireNonNull(effectiveDate, "effectiveDate");
        this.status = Objects.requireNonNull(status, "status");
        this.targetOrgUnitId = targetOrgUnitId;
        this.targetPositionId = targetPositionId;
        this.targetReportsToEmployeeId = targetReportsToEmployeeId;
        this.isTargetFeeEarner = isTargetFeeEarner;
        this.targetEmploymentPhaseKind = targetEmploymentPhaseKind;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = createdAt;
        this.appliedAt = appliedAt;
    }

    /**
     * A command about to be stored. A separation is entered with the last working day and takes effect the day after (ADR-0068 item 1.5); every other
     * kind carries its effective date. A day that is not after today takes effect at once (ADR-0068 item 1.8), a later one waits.
     *
     * @param employee              the employee the command is about: the one the request names, or for a hire the id the server made
     * @param employeeEventSequence the employee's event sequence, which the command's own event continues
     */
    public static PersonnelCommand enter(PersonnelCommandId id, EmployeeId employee, EnterPersonnelCommand request, UserId createdBy, Instant now, LocalDate today,
                                         long employeeEventSequence) {
        PersonnelCommandKind kind = require(request.commandKind(), "a command kind");
        LocalDate effective;
        if (kind == PersonnelCommandKind.SEPARATE) {
            if (request.effectiveDate() != null) {
                throw invalid("a separation is entered with the last working day, not an effective date");
            }
            effective = require(request.lastWorkingDay(), "the last working day").plusDays(1);
        } else {
            if (request.lastWorkingDay() != null) {
                throw invalid("only a separation carries a last working day");
            }
            effective = require(request.effectiveDate(), "the effective date");
        }
        // A correction is immediate whatever its day (ck_personnel_commands_correction_immediate); it never waits.
        CommandStatus status = kind != PersonnelCommandKind.CORRECTION && effective.isAfter(today) ? CommandStatus.PENDING : CommandStatus.EFFECTIVE;
        PersonnelCommand command = new PersonnelCommand(id, employee, kind, effective, status, request.targetOrgUnitId(),
                request.targetPositionId(), request.targetReportsToEmployeeId(), request.isTargetFeeEarner(), request.targetEmploymentPhaseKind(), createdBy, now,
                status == CommandStatus.EFFECTIVE ? now : null, 0, employeeEventSequence);
        command.requireShapeOfKind();
        return command;
    }

    /** A stored command. */
    public static PersonnelCommand restore(PersonnelCommandId id, EmployeeId employeeId, PersonnelCommandKind kind, LocalDate effectiveDate, CommandStatus status,
                                           @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, @Nullable EmployeeId targetReportsToEmployeeId,
                                           @Nullable Boolean isTargetFeeEarner, @Nullable EmploymentPhaseKind targetEmploymentPhaseKind, UserId createdBy,
                                           Instant createdAt, @Nullable Instant appliedAt, long version, long employeeEventSequence) {
        return new PersonnelCommand(id, employeeId, kind, effectiveDate, status, targetOrgUnitId, targetPositionId, targetReportsToEmployeeId, isTargetFeeEarner,
                targetEmploymentPhaseKind, createdBy, createdAt, appliedAt, version, employeeEventSequence);
    }

    /** Stored columns that must hold for the kind (ck_personnel_commands_*_columns). */
    private void requireShapeOfKind() {
        int targets = count(targetOrgUnitId, targetPositionId, targetReportsToEmployeeId, isTargetFeeEarner);
        switch (kind) {
            case HIRE, REINSTATE -> {
                if (targetOrgUnitId == null || targetPositionId == null || isTargetFeeEarner == null || targetEmploymentPhaseKind == null) {
                    throw invalid("a " + kind + " names the department, the position, the fee-earner flag and the phase");
                }
            }
            case TRANSFER -> {
                if (targets == 0 || targetEmploymentPhaseKind != null) {
                    throw invalid("a TRANSFER names what changes: department, position, manager or fee-earner flag");
                }
            }
            case CORRECTION -> {
                if (targets + count(targetEmploymentPhaseKind) == 0) {
                    throw invalid("a CORRECTION names what it corrects: department, position, manager, fee-earner flag or phase");
                }
            }
            case REGULARIZE, SEPARATE, UNPAID_LEAVE_START, UNPAID_LEAVE_END -> {
                if (targets + count(targetEmploymentPhaseKind) > 0) {
                    throw invalid("a " + kind + " carries no target");
                }
            }
        }
        if (employeeId.equals(targetReportsToEmployeeId)) {
            throw invalid("an employee does not report to themself");
        }
    }

    /** The administrator-separation hold decided at entry (ADR-0068 item 2); only a separation can be held, and nothing was applied yet. */
    public void holdAtEntry() {
        if (kind != PersonnelCommandKind.SEPARATE) {
            throw invalid("only a separation can be held");
        }
        status = CommandStatus.HELD;
        appliedAt = null;
    }

    /**
     * A held separation is released (ADR-0068 item 2.3): it takes effect now when its original effective date is not after today, otherwise it waits
     * as pending for the due-date job. Either way it is written at the original date and never held again.
     */
    public void releaseHeld(Instant at, LocalDate today) {
        boolean due = !effectiveDate.isAfter(today);
        status = CommandStatus.MACHINE.require(status, due ? CommandStatus.EFFECTIVE : CommandStatus.PENDING, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        appliedAt = due ? Objects.requireNonNull(at, "at") : null;
    }

    /** The due-day re-judgement of a separation that was not held at entry found a reason to hold it (ADR-0068 item 2.2): nothing was applied, the versions are voided by the caller. */
    public void holdOnDueDay() {
        if (kind != PersonnelCommandKind.SEPARATE) {
            throw invalid("only a separation can be held");
        }
        status = CommandStatus.MACHINE.require(status, CommandStatus.HELD, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        appliedAt = null;
    }

    /** A pending command whose day has come takes effect (the due-date job; its versions were written when it was entered or released). */
    public void dueEffect(Instant at, LocalDate today) {
        if (effectiveDate.isAfter(today)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "a command takes effect on its effective date, not before");
        }
        status = CommandStatus.MACHINE.require(status, CommandStatus.EFFECTIVE, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        appliedAt = Objects.requireNonNull(at, "at");
    }

    /** Records that the separation was held instead of taking effect (ADR-0068 item 2.2); the employee stays employed and no separation is published. */
    public void recordHeld(SeparationHoldId hold, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.held(this, hold, legalEntity));
    }

    /** Records the release of the hold; released by is empty when the operations command released it. */
    public void recordReleased(SeparationHoldId hold, @Nullable UserId releasedBy, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.released(this, hold, releasedBy, legalEntity));
    }

    /** Takes a pending or held command back; the reason is 1 to 500 characters and not blank (ADR-0068 item 1.6). */
    public void withdraw(UserId by, @Nullable String reason, Instant at) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_REASON_INVALID, ErrorKind.UNPROCESSABLE,
                    "a withdrawal needs a reason of 1 to " + MAX_REASON_LENGTH + " characters that is not blank");
        }
        if (!status.isOpen()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "only a pending or held command can be withdrawn, this one is " + status);
        }
        status = CommandStatus.MACHINE.require(status, CommandStatus.WITHDRAWN, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        withdrawnBy = Objects.requireNonNull(by, "by");
        withdrawnAt = at;
        withdrawalReason = reason;
    }

    /**
     * Records the event of a command that took effect (nothing for unpaid leave, whose events are not named yet, contract question Q1).
     *
     * @param assignmentBefore the assignment the day before the effective date, if any; only a transfer reads it
     * @param legalEntity the employee's legal entity on the effective date, null when none can be resolved
     */
    public void recordEffect(@Nullable VersionRow assignmentBefore, @Nullable LegalEntityId legalEntity) {
        PersonnelCommandEvents.of(this, assignmentBefore, legalEntity).ifPresent(this::record);
    }

    public PersonnelCommandId id() {
        return id;
    }

    public EmployeeId employeeId() {
        return employeeId;
    }

    public PersonnelCommandKind kind() {
        return kind;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public CommandStatus status() {
        return status;
    }

    public @Nullable OrgUnitId targetOrgUnitId() {
        return targetOrgUnitId;
    }

    public @Nullable PositionId targetPositionId() {
        return targetPositionId;
    }

    public @Nullable EmployeeId targetReportsToEmployeeId() {
        return targetReportsToEmployeeId;
    }

    public @Nullable Boolean isTargetFeeEarner() {
        return isTargetFeeEarner;
    }

    public @Nullable EmploymentPhaseKind targetEmploymentPhaseKind() {
        return targetEmploymentPhaseKind;
    }

    public UserId createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public @Nullable Instant appliedAt() {
        return appliedAt;
    }

    public @Nullable Instant withdrawnAt() {
        return withdrawnAt;
    }

    public @Nullable UserId withdrawnBy() {
        return withdrawnBy;
    }

    public @Nullable String withdrawalReason() {
        return withdrawalReason;
    }

    private static int count(@Nullable Object... values) {
        int n = 0;
        for (Object value : values) {
            n += value == null ? 0 : 1;
        }
        return n;
    }

    private static <T> T require(@Nullable T value, String what) {
        if (value == null) {
            throw invalid(what + " is required");
        }
        return value;
    }

    private static DomainException invalid(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, message);
    }
}
