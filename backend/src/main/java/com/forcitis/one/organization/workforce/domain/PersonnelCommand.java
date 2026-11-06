package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
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
    private final @Nullable LegalEntityId targetLegalEntityId;
    private @Nullable LegalEntityId countersignPreviousLegalEntityId;
    private @Nullable LegalEntityId countersignNewLegalEntityId;
    private @Nullable LegalEntityId approvedPreviousLegalEntityId;
    private @Nullable LegalEntityId approvedNewLegalEntityId;
    private @Nullable AssignmentId targetAssignmentId;
    private @Nullable AssignmentKind targetAssignmentKind;
    private final @Nullable OrgUnitId actingCoveredOrgUnitId;
    private final @Nullable PositionId actingCoveredPositionId;
    private final @Nullable EmployeeId actingCoveredEmployeeId;
    private final @Nullable LocalDate actingEndsBeforeDate;
    private final @Nullable ActingDutyId targetActingDutyId;
    private final @Nullable String reason;
    private final UserId createdBy;
    private final Instant createdAt;
    private CommandStatus status;
    private @Nullable Instant appliedAt;
    private @Nullable Instant withdrawnAt;
    private @Nullable UserId withdrawnBy;
    private @Nullable String withdrawalReason;
    private @Nullable OrgStructureCommandId orgStructureCommandId;
    private @Nullable EmploymentRelationshipId employmentRelationshipId;

    private PersonnelCommand(PersonnelCommandId id, EmployeeId employeeId, PersonnelCommandKind kind, LocalDate effectiveDate, CommandStatus status,
                             @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, @Nullable EmployeeId targetReportsToEmployeeId,
                             @Nullable Boolean isTargetFeeEarner, @Nullable EmploymentPhaseKind targetEmploymentPhaseKind, @Nullable LegalEntityId targetLegalEntityId,
                             @Nullable LegalEntityId countersignPreviousLegalEntityId, @Nullable LegalEntityId countersignNewLegalEntityId,
                             @Nullable AssignmentId targetAssignmentId, @Nullable AssignmentKind targetAssignmentKind, UserId createdBy,
                             Instant createdAt, @Nullable Instant appliedAt, long version, long eventSequence,
                             @Nullable OrgUnitId actingCoveredOrgUnitId, @Nullable PositionId actingCoveredPositionId,
                             @Nullable EmployeeId actingCoveredEmployeeId, @Nullable LocalDate actingEndsBeforeDate,
                             @Nullable ActingDutyId targetActingDutyId, @Nullable String reason) {
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
        this.targetLegalEntityId = targetLegalEntityId;
        this.countersignPreviousLegalEntityId = countersignPreviousLegalEntityId;
        this.countersignNewLegalEntityId = countersignNewLegalEntityId;
        this.targetAssignmentId = targetAssignmentId;
        this.targetAssignmentKind = targetAssignmentKind;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = createdAt;
        this.appliedAt = appliedAt;
        this.actingCoveredOrgUnitId = actingCoveredOrgUnitId;
        this.actingCoveredPositionId = actingCoveredPositionId;
        this.actingCoveredEmployeeId = actingCoveredEmployeeId;
        this.actingEndsBeforeDate = actingEndsBeforeDate;
        this.targetActingDutyId = targetActingDutyId;
        this.reason = reason;
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
        if (kind == PersonnelCommandKind.ACTING_EXTEND && !effective.equals(today)) {
            throw invalid("an acting extension takes the business day of entry as its effective date");
        }
        // ck_personnel_commands_correction_immediate also covers extensions.
        CommandStatus status = kind != PersonnelCommandKind.CORRECTION && kind != PersonnelCommandKind.ACTING_EXTEND && effective.isAfter(today) ? CommandStatus.PENDING : CommandStatus.EFFECTIVE;
        PersonnelCommand command = new PersonnelCommand(id, employee, kind, effective, status, request.targetOrgUnitId(),
                request.targetPositionId(), request.targetReportsToEmployeeId(), request.isTargetFeeEarner(), request.targetEmploymentPhaseKind(),
                request.targetLegalEntityId(), null, null, request.targetAssignmentId(), null, createdBy, now,
                status == CommandStatus.EFFECTIVE ? now : null, 0, employeeEventSequence,
                request.actingCoveredOrgUnitId(), request.actingCoveredPositionId(), request.actingCoveredEmployeeId(), request.actingEndsBeforeDate(), request.targetActingDutyId(), request.reason());
        command.requireShapeOfKind();
        if (request.employmentRelationshipId() != null) {
            command.bindTo(request.employmentRelationshipId());
        }
        return command;
    }

    /** A stored command. */
    public static PersonnelCommand restore(PersonnelCommandId id, EmployeeId employeeId, PersonnelCommandKind kind, LocalDate effectiveDate, CommandStatus status,
                                           @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, @Nullable EmployeeId targetReportsToEmployeeId,
                                           @Nullable Boolean isTargetFeeEarner, @Nullable EmploymentPhaseKind targetEmploymentPhaseKind,
                                           @Nullable LegalEntityId targetLegalEntityId, @Nullable LegalEntityId countersignPreviousLegalEntityId,
                                           @Nullable LegalEntityId countersignNewLegalEntityId, @Nullable AssignmentId targetAssignmentId,
                                           @Nullable AssignmentKind targetAssignmentKind, UserId createdBy,
                                           Instant createdAt, @Nullable Instant appliedAt, long version, long employeeEventSequence) {
        return restore(id, employeeId, kind, effectiveDate, status, targetOrgUnitId, targetPositionId, targetReportsToEmployeeId,
                isTargetFeeEarner, targetEmploymentPhaseKind, targetLegalEntityId, countersignPreviousLegalEntityId, countersignNewLegalEntityId,
                targetAssignmentId, targetAssignmentKind, createdBy, createdAt, appliedAt, version, employeeEventSequence, null, null, null, null, null, null);
    }

    /** A stored command including its acting-duty fields. */
    public static PersonnelCommand restore(PersonnelCommandId id, EmployeeId employeeId, PersonnelCommandKind kind, LocalDate effectiveDate, CommandStatus status,
                                           @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, @Nullable EmployeeId targetReportsToEmployeeId,
                                           @Nullable Boolean isTargetFeeEarner, @Nullable EmploymentPhaseKind targetEmploymentPhaseKind,
                                           @Nullable LegalEntityId targetLegalEntityId, @Nullable LegalEntityId countersignPreviousLegalEntityId,
                                           @Nullable LegalEntityId countersignNewLegalEntityId, @Nullable AssignmentId targetAssignmentId,
                                           @Nullable AssignmentKind targetAssignmentKind, UserId createdBy,
                                           Instant createdAt, @Nullable Instant appliedAt, long version, long employeeEventSequence,
                                           @Nullable OrgUnitId actingCoveredOrgUnitId, @Nullable PositionId actingCoveredPositionId,
                                           @Nullable EmployeeId actingCoveredEmployeeId, @Nullable LocalDate actingEndsBeforeDate,
                                           @Nullable ActingDutyId targetActingDutyId, @Nullable String reason) {
        return new PersonnelCommand(id, employeeId, kind, effectiveDate, status, targetOrgUnitId, targetPositionId, targetReportsToEmployeeId, isTargetFeeEarner,
                targetEmploymentPhaseKind, targetLegalEntityId, countersignPreviousLegalEntityId, countersignNewLegalEntityId, targetAssignmentId, targetAssignmentKind,
                createdBy, createdAt, appliedAt, version, employeeEventSequence, actingCoveredOrgUnitId, actingCoveredPositionId, actingCoveredEmployeeId, actingEndsBeforeDate, targetActingDutyId, reason);
    }

    /**
     * The relationship the command is about (ADR-0086, design section 5): a hire's is made at entry and bound here, every other kind but a change of legal entity names it,
     * and a change of legal entity has none. Bound once, before the command is stored.
     */
    public void bindTo(EmploymentRelationshipId relationship) {
        if (kind == PersonnelCommandKind.CHANGE_LEGAL_ENTITY) {
            throw invalid("a CHANGE_LEGAL_ENTITY is about the person and names no employment relationship");
        }
        if (employmentRelationshipId != null && !employmentRelationshipId.equals(relationship)) {
            throw invalid("the command is already bound to another employment relationship");
        }
        employmentRelationshipId = Objects.requireNonNull(relationship, "relationship");
    }

    public @Nullable EmploymentRelationshipId employmentRelationshipId() {
        return employmentRelationshipId;
    }

    /** The merge that generated this transfer (ck_personnel_commands_org_structure_command: only a transfer); call before the command is stored. */
    public void generatedBy(OrgStructureCommandId merge) {
        if (kind != PersonnelCommandKind.TRANSFER) {
            throw invalid("only a transfer can be generated by a merge");
        }
        this.orgStructureCommandId = merge;
    }

    public @Nullable OrgStructureCommandId orgStructureCommandId() {
        return orgStructureCommandId;
    }

    /** Stored columns that must hold for the kind (ck_personnel_commands_*_columns). */
    private void requireShapeOfKind() {
        if (kind == PersonnelCommandKind.ACTING_START) {
            ActingDutyRules.requireStart(this);
        } else if (kind == PersonnelCommandKind.ACTING_END || kind == PersonnelCommandKind.ACTING_EXTEND) {
            ActingDutyRules.requireChange(this);
        } else if (count(actingCoveredOrgUnitId, actingCoveredPositionId, actingCoveredEmployeeId, actingEndsBeforeDate, targetActingDutyId, reason) != 0) {
            throw invalid("only an acting command carries acting-duty fields or a reason");
        }
        int targets = count(targetOrgUnitId, targetPositionId, targetReportsToEmployeeId, isTargetFeeEarner);
        switch (kind) {
            case HIRE, REINSTATE -> {
                if (isTargetFeeEarner == null || targetEmploymentPhaseKind == null || targetReportsToEmployeeId != null && targetOrgUnitId == null) {
                    throw invalid("a " + kind + " names the fee-earner flag and the phase");
                }
                if ((targetOrgUnitId == null) != (targetPositionId == null)) {
                    throw invalid("a " + kind + " names the department and the position together, or neither (an additional employer without a post)");
                }
            }
            case TRANSFER -> {
                if (targets + count(targetLegalEntityId) == 0 || targetEmploymentPhaseKind != null) {
                    throw invalid("a TRANSFER names what changes: department, position, manager, fee-earner flag or legal entity");
                }
            }
            case CORRECTION -> {
                if (targets + count(targetEmploymentPhaseKind, targetLegalEntityId) == 0) {
                    throw invalid("a CORRECTION names what it corrects: department, position, manager, fee-earner flag, phase or legal entity");
                }
            }
            case START_CONCURRENT -> {
                if (targetOrgUnitId == null || targetPositionId == null || targetReportsToEmployeeId != null || isTargetFeeEarner != null
                        || targetEmploymentPhaseKind != null) {
                    throw invalid("a START_CONCURRENT names the department and the position, and no manager, fee-earner flag or phase: a concurrent post has no reporting line");
                }
            }
            case END_CONCURRENT -> {
                if (targets + count(targetEmploymentPhaseKind) > 0) {
                    throw invalid("an END_CONCURRENT carries no target but the post it ends");
                }
            }
            case REGULARIZE, SEPARATE, UNPAID_LEAVE_START, UNPAID_LEAVE_END -> {
                if (targets + count(targetEmploymentPhaseKind) > 0) {
                    throw invalid("a " + kind + " carries no target");
                }
            }
            case ACTING_START, ACTING_END, ACTING_EXTEND -> {
                if (targets + count(targetEmploymentPhaseKind, targetAssignmentId, targetLegalEntityId) != 0) {
                    throw invalid("an acting command carries no assignment or attribution targets");
                }
            }
            case CHANGE_LEGAL_ENTITY -> {
                if (targetLegalEntityId == null || targets + count(targetEmploymentPhaseKind) > 0) {
                    throw invalid("a CHANGE_LEGAL_ENTITY names the legal entity and nothing else");
                }
            }
        }
        if (targetLegalEntityId != null && kind != PersonnelCommandKind.CHANGE_LEGAL_ENTITY && kind != PersonnelCommandKind.TRANSFER && kind != PersonnelCommandKind.CORRECTION) {
            throw invalid("a " + kind + " carries no target legal entity");
        }
        if (employeeId.equals(targetReportsToEmployeeId)) {
            throw invalid("an employee does not report to themself");
        }
    }

    /**
     * Settles which post the command acts on and stores it with the command (#711): a transfer, and a correction that names a department, a position
     * or a manager, act on the post they name, or on the primary post when they name none; an end of a concurrent post must name its post; every other
     * kind names none. A concurrent post has no manager and no fee-earner flag, so a command on one carries neither. Call after the history is read and
     * before the command is stored.
     *
     * @param primaryRoot the person's primary root, made when it does not exist yet
     */
    public void actOnPost(PersonnelHistory history, Supplier<AssignmentRoot> primaryRoot) {
        boolean names = kind == PersonnelCommandKind.TRANSFER || kind == PersonnelCommandKind.CORRECTION
                && (targetOrgUnitId != null || targetPositionId != null || targetReportsToEmployeeId != null);
        if (!names && kind != PersonnelCommandKind.END_CONCURRENT) {
            if (targetAssignmentId != null) {
                throw invalid("a " + kind + " names no post");
            }
            return;
        }
        if (kind == PersonnelCommandKind.END_CONCURRENT && targetAssignmentId == null) {
            throw invalid("an END_CONCURRENT names the concurrent post it ends");
        }
        AssignmentRoot root = targetAssignmentId == null ? primaryRoot.get()
                : history.root(targetAssignmentId).orElseThrow(() -> invalid("the post does not belong to the employee"));
        if (kind == PersonnelCommandKind.END_CONCURRENT && root.kind() != AssignmentKind.CONCURRENT) {
            throw invalid("the primary post is not ended by itself: separate the employee");
        }
        if (root.kind() == AssignmentKind.CONCURRENT && (targetReportsToEmployeeId != null || isTargetFeeEarner != null || targetEmploymentPhaseKind != null)) {
            throw invalid("a concurrent post has no manager and no fee-earner flag");
        }
        if (root.kind() == AssignmentKind.CONCURRENT && targetLegalEntityId != null) {
            throw invalid("a concurrent post carries no legal entity: the cost legal entity follows the primary post");
        }
        targetAssignmentId = root.id();
        targetAssignmentKind = root.kind();
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
     * The cost legal entity changes between before and after the command (ADR-0073 item 3.4): the command waits for the finance countersign at entry, the basis is
     * recorded and never changes, and no version is written until every required countersign is approved.
     */
    public void requireCountersign(LegalEntityId previous, LegalEntityId next) {
        if (previous.equals(next) || countersignNewLegalEntityId != null) {
            throw invalid("a countersign basis names two different legal entities, once");
        }
        if (version() != 0 || (status != CommandStatus.PENDING && status != CommandStatus.EFFECTIVE)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL, ErrorKind.CONFLICT, "only a command being entered waits for the countersign");
        }
        countersignPreviousLegalEntityId = previous;
        countersignNewLegalEntityId = next;
        status = CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN;
        appliedAt = null;
    }

    /**
     * A transfer a countersigned merge generates whose cost legal entity change is the one the merge's heads approved: it waits for nothing and stores no basis (the merge
     * holds it), but the change is announced when the command takes effect, like that of a command that waited.
     */
    public void changeApprovedByMerge(LegalEntityId previous, LegalEntityId next) {
        if (orgStructureCommandId == null || previous.equals(next)) {
            throw invalid("only a transfer of a merge carries the change its heads approved, and only a real change");
        }
        approvedPreviousLegalEntityId = previous;
        approvedNewLegalEntityId = next;
    }

    /**
     * Every required countersign is approved: the command is pending, or effective when its day has come; its versions are written by the caller in the same
     * transaction. A correction is immediate whatever its day (ck_personnel_commands_correction_immediate), so it becomes effective here too.
     */
    public void completeCountersign(Instant at, LocalDate today) {
        boolean due = !effectiveDate.isAfter(today) || kind == PersonnelCommandKind.CORRECTION;
        status = leave(CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN, due ? CommandStatus.EFFECTIVE : CommandStatus.PENDING);
        appliedAt = due ? Objects.requireNonNull(at, "at") : null;
    }

    /**
     * A held separation is released (ADR-0068 item 2.3): it takes effect now when its original effective date is not after today, otherwise it waits
     * as pending for the due-date job. Either way it is written at the original date and never held again.
     */
    public void releaseHeld(Instant at, LocalDate today) {
        boolean due = !effectiveDate.isAfter(today);
        status = leave(CommandStatus.HELD, due ? CommandStatus.EFFECTIVE : CommandStatus.PENDING);
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
        status = leave(CommandStatus.PENDING, CommandStatus.EFFECTIVE);
        appliedAt = Objects.requireNonNull(at, "at");
    }

    /** A method that is for one status only (its design names it): any other status is refused before the machine is asked. */
    private CommandStatus leave(CommandStatus source, CommandStatus target) {
        if (status != source) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL, ErrorKind.CONFLICT, "this is for a " + source + " command, this one is " + status);
        }
        return CommandStatus.MACHINE.require(status, target, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
    }

    /** Records that the separation was held instead of taking effect (ADR-0068 item 2.2); the employee stays employed and no separation is published. */
    public void recordHeld(SeparationHoldId hold, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.held(this, hold, legalEntity));
    }

    /** Records the release of the hold; released by is empty when the operations command released it. */
    public void recordReleased(SeparationHoldId hold, @Nullable UserId releasedBy, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.released(this, hold, releasedBy, legalEntity));
    }

    /**
     * The drafter's wish to take a countersigned pending command back (ADR-0073 item 3.6, user answer Q2): the command stands, finance decides, so only the reason and the
     * state are judged here. Once the day has come the versions are in force whatever the due-date job has done yet, and only a countersigned correction undoes them.
     */
    public void requireWithdrawalAllowed(@Nullable String reason, LocalDate today) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_REASON_INVALID, ErrorKind.UNPROCESSABLE,
                    "a withdrawal needs a reason of 1 to " + MAX_REASON_LENGTH + " characters that is not blank");
        }
        if (status != CommandStatus.PENDING || countersignNewLegalEntityId == null || !effectiveDate.isAfter(today)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "a countersigned command can be asked to be withdrawn only while it is pending and its day has not come");
        }
    }

    /** Takes a pending, held or awaiting command back; the reason is 1 to 500 characters and not blank (ADR-0068 item 1.6). */
    public void withdraw(UserId by, @Nullable String reason, Instant at) {
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_REASON_INVALID, ErrorKind.UNPROCESSABLE,
                    "a withdrawal needs a reason of 1 to " + MAX_REASON_LENGTH + " characters that is not blank");
        }
        if (!status.isOpen()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                    "only a pending, held or awaiting command can be withdrawn, this one is " + status);
        }
        status = CommandStatus.MACHINE.require(status, CommandStatus.WITHDRAWN, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        withdrawnBy = Objects.requireNonNull(by, "by");
        withdrawnAt = at;
        withdrawalReason = reason;
    }

    /** Records the duty fact created by this effective command, on the employee event stream. */
    public void recordActingStarted(ActingDuty duty, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.actingStarted(this, duty, legalEntity));
    }

    public void recordActingEnded(ActingDuty duty, @Nullable LegalEntityId legalEntity) {
        record(PersonnelCommandEvents.actingEnded(this, duty, id, effectiveDate, ActingDutyEndCause.ENDED_EARLY, legalEntity));
    }

    public void recordActingSeparation(ActingDuty duty, @Nullable LegalEntityId legalEntity) {
        if (kind != PersonnelCommandKind.SEPARATE || status != CommandStatus.EFFECTIVE || !employeeId.equals(duty.employeeId())) {
            throw invalid("a duty's separation end is recorded by its employee's effective separation");
        }
        record(PersonnelCommandEvents.actingEnded(this, duty, id, effectiveDate, ActingDutyEndCause.SEPARATION, legalEntity));
    }

    /** The start command carries the employee's current event sequence; expiry creates no command and changes no command state. */
    public void recordActingExpiry(ActingDuty duty, @Nullable LegalEntityId legalEntity) {
        if (!id.equals(duty.sourcePersonnelCommandId()) || !employeeId.equals(duty.employeeId())) {
            throw invalid("an expiry is recorded on the duty's source command");
        }
        record(PersonnelCommandEvents.actingEnded(this, duty, null, duty.endsBefore(), ActingDutyEndCause.EXPIRED, legalEntity));
    }

    /**
     * Records the event of a command that took effect (nothing for unpaid leave, whose events are not named yet, contract question Q1).
     *
     * @param assignmentBefore a transfer's: the assignment the day before the effective date, if any; a correction's: the assignment today before it; others ignore it
     * @param assignmentAfter a correction's: the assignment today after it, if any; others pass null
     * @param legalEntity the employee's legal entity on the effective date, null when none can be resolved
     */
    public void recordEffect(@Nullable VersionRow assignmentBefore, @Nullable VersionRow assignmentAfter, @Nullable LegalEntityId legalEntity, PersonnelHistory history) {
        PersonnelCommandEvents.PersonTransition person = personTransition(history);
        PersonnelCommandEvents.of(this, assignmentBefore, assignmentAfter, legalEntity, person).forEach(this::record);
        if (countersignNewLegalEntityId != null) {
            record(PersonnelCommandEvents.legalEntityChanged(this, Objects.requireNonNull(countersignPreviousLegalEntityId), countersignNewLegalEntityId));
        } else if (approvedNewLegalEntityId != null) {
            record(PersonnelCommandEvents.legalEntityChanged(this, Objects.requireNonNull(approvedPreviousLegalEntityId), approvedNewLegalEntityId));
        }
    }

    /** What the command did to the person as a whole, read from the employee's history at the moment it takes effect. */
    private PersonnelCommandEvents.PersonTransition personTransition(PersonnelHistory history) {
        if (employmentRelationshipId == null) {
            return PersonnelCommandEvents.PersonTransition.NONE;
        }
        EmploymentRelationshipId rel = employmentRelationshipId;
        boolean enters = kind != PersonnelCommandKind.HIRE && kind != PersonnelCommandKind.REINSTATE || history.personEnters(rel, effectiveDate);
        boolean leaves = kind != PersonnelCommandKind.SEPARATE || history.personLeaves(rel, effectiveDate)
                && history.commands().stream().noneMatch(o -> o.kind() == PersonnelCommandKind.HIRE && o.status() != CommandStatus.WITHDRAWN && o.effectiveDate().equals(effectiveDate)
                        && history.relationship(o.employmentRelationshipId() == null ? rel : o.employmentRelationshipId())
                                .map(r -> rel.equals(r.predecessorId())).orElse(false));
        return new PersonnelCommandEvents.PersonTransition(enters, leaves);
    }

    public @Nullable OrgUnitId actingCoveredOrgUnitId() {
        return actingCoveredOrgUnitId;
    }

    public @Nullable PositionId actingCoveredPositionId() {
        return actingCoveredPositionId;
    }

    public @Nullable EmployeeId actingCoveredEmployeeId() {
        return actingCoveredEmployeeId;
    }

    public @Nullable LocalDate actingEndsBeforeDate() {
        return actingEndsBeforeDate;
    }

    public @Nullable ActingDutyId targetActingDutyId() {
        return targetActingDutyId;
    }

    public @Nullable String reason() {
        return reason;
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

    public @Nullable LegalEntityId targetLegalEntityId() {
        return targetLegalEntityId;
    }

    /** The cost legal entity before the command, as recorded when it began to wait; empty when it never needed a countersign. */
    public @Nullable LegalEntityId countersignPreviousLegalEntityId() {
        return countersignPreviousLegalEntityId;
    }

    /** The cost legal entity after the command, as recorded when it began to wait; empty when it never needed a countersign. */
    public @Nullable LegalEntityId countersignNewLegalEntityId() {
        return countersignNewLegalEntityId;
    }

    public @Nullable AssignmentId targetAssignmentId() {
        return targetAssignmentId;
    }

    public @Nullable AssignmentKind targetAssignmentKind() {
        return targetAssignmentKind;
    }

    /** Whether the command is about a concurrent post: a post the workforce events, the data scope and the approvals do not read (#711). */
    public boolean isOnConcurrentPost() {
        return kind == PersonnelCommandKind.START_CONCURRENT || targetAssignmentKind == AssignmentKind.CONCURRENT;
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
