package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetApprovalWithdrawn;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.RoutingFlag;
import com.forcitis.one.platform.approval.events.DocumentSubmittedForApproval;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.cost.timesheet.api.TimesheetDocument;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A timesheet moves DRAFT, SUBMITTED, then APPROVED or REJECTED; a return gives it back to its owner for changes (RETURNED), a cancel
 * gives it back as a draft, and a rejected one may be changed and submitted again as the same timesheet. Only APPROVED has no way out.
 * The approval module owns the process state, this aggregate owns the timesheet's state, and only events join them. The work date is what the employee entered and never changes afterwards; the day of submission is a separate fact.
 */
public final class Timesheet extends AggregateRoot {

    private static final Logger log = LoggerFactory.getLogger(Timesheet.class);

    public static final StateMachine<TimesheetStatus> MACHINE = StateMachine.of(TimesheetStatus.class)
            .allow(TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED)
            .allow(TimesheetStatus.RETURNED, TimesheetStatus.SUBMITTED)
            .allow(TimesheetStatus.REJECTED, TimesheetStatus.SUBMITTED)
            .allow(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED, TimesheetStatus.RETURNED,
                    TimesheetStatus.DRAFT)
            .build();

    private final TimesheetId timesheetId;
    private final DocumentNumber number;
    private final NumberingRuleVersionId numberingRuleVersionId;
    private final EmployeeId employee;
    private final LegalEntityId legalEntityId;
    private final OrgUnitId orgUnitId;
    private final LocalDate workDate;
    private final TimesheetStatus loadedStatus;
    private final boolean isNew;
    private final List<AggregateReference> touchedTogether;
    private Hours submittedHours;
    private EmployeeId projectManager;
    private TimesheetStatus status;
    private int round;
    private LocalDate submittedOn;

    private Timesheet(TimesheetId timesheetId, DocumentNumber number, NumberingRuleVersionId numberingRuleVersionId,
            EmployeeId employee, LegalEntityId legalEntityId, OrgUnitId orgUnitId,
            LocalDate workDate, Hours submittedHours, EmployeeId projectManager, TimesheetStatus status,
            int round, LocalDate submittedOn, List<AggregateReference> touchedTogether, long version, long eventSequence, boolean isNew) {
        super(version, eventSequence);
        this.timesheetId = Objects.requireNonNull(timesheetId, "timesheetId");
        this.number = Objects.requireNonNull(number, "number");
        this.numberingRuleVersionId = numberingRuleVersionId;
        this.employee = Objects.requireNonNull(employee, "employee");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.orgUnitId = Objects.requireNonNull(orgUnitId, "orgUnitId");
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.submittedHours = Objects.requireNonNull(submittedHours, "submittedHours");
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager");
        this.status = Objects.requireNonNull(status, "status");
        this.round = round;
        this.submittedOn = submittedOn;
        this.touchedTogether = List.copyOf(touchedTogether);
        this.loadedStatus = status;
        this.isNew = isNew;
    }

    /**
     * counter: the numbering aggregate that took a number in the same transaction as this timesheet is created; the events of this
     * unit of work name it as a related aggregate. ruleVersionId: the numbering rule version the number was issued under.
     */
    public static Timesheet draft(TimesheetId timesheetId, DocumentNumber number, AggregateReference counter, NumberingRuleVersionId ruleVersionId,
            EmployeeId employee,
            LegalEntityId legalEntityId, OrgUnitId orgUnitId, LocalDate workDate, Hours submittedHours,
            EmployeeId projectManager, LocalDate today) {
        if (workDate.isAfter(today)) {
            throw TimesheetError.COST_TIMESHEET_WORK_DATE_IN_FUTURE.refusal("The work date " + workDate + " is after today, " + today);
        }
        if (ruleVersionId == null) {
            log.error("ALERT P2 cost.timesheet timesheet {} was issued a number without the numbering rule version it was issued under",
                    timesheetId.value());
            throw new IllegalStateException("A new timesheet needs the numbering rule version its number was issued under");
        }
        return new Timesheet(timesheetId, number, ruleVersionId, employee, legalEntityId, orgUnitId, workDate, submittedHours,
                projectManager, TimesheetStatus.DRAFT, 0, null, List.of(counter), 0, 0, true);
    }

    /** ruleVersionId is empty for a timesheet carried over from the old system, whose number was not issued here. */
    public static Timesheet restore(TimesheetId timesheetId, DocumentNumber number, NumberingRuleVersionId ruleVersionId,
            EmployeeId employee, LegalEntityId legalEntityId,
            OrgUnitId orgUnitId, LocalDate workDate, Hours submittedHours, EmployeeId projectManager,
            TimesheetStatus status, int round, LocalDate submittedOn, long version, long eventSequence) {
        return new Timesheet(timesheetId, number, ruleVersionId, employee, legalEntityId, orgUnitId, workDate, submittedHours,
                projectManager, status, round, submittedOn, List.of(), version, eventSequence, false);
    }

    public void revise(EmployeeId by, LocalDate newWorkDate, Hours newHours, EmployeeId newProjectManager) {
        requireOwner(by);
        if (!status.isEditable()) {
            throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("The timesheet cannot be changed while it is " + status);
        }
        if (!workDate.equals(newWorkDate)) {
            throw TimesheetError.COST_TIMESHEET_WORK_DATE_FIXED.refusal(
                    "The work date " + workDate + " was entered with the timesheet and is not rewritten by a resubmission");
        }
        submittedHours = Objects.requireNonNull(newHours, "newHours");
        projectManager = Objects.requireNonNull(newProjectManager, "newProjectManager");
    }

    public void submit(EmployeeId by, LocalDate today) {
        requireOwner(by);
        status = MACHINE.require(status, TimesheetStatus.SUBMITTED);
        round++;
        submittedOn = Objects.requireNonNull(today, "today");
        Snapshot at = snapshot();
        List<TimesheetRoutingFlag> flags = List.of(TimesheetRoutingFlag.PROJECT_HOURS);
        ContentHash hash = contentHash();
        ApprovalSummary summary = new ApprovalSummary(number.value() + " " + workDate + " " + submittedHours.value().toPlainString() + "h");
        record("cost.timesheet.TimesheetSubmitted", envelope -> new TimesheetSubmitted(envelope, at.document(), number, at.employee(),
                projectManager, at.submittedHours(), at.submittedOn(), new RoundNumber(at.round())));
        List<RoutingFlag> routing = flags.stream().map(flag -> new RoutingFlag(flag.flagName())).toList();
        List<ApprovalField> fields = List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                List.of(String.valueOf(projectManager.value()))));
        record("platform.approval.DocumentSubmittedForApproval", envelope -> new DocumentSubmittedForApproval(envelope, TimesheetApprovalProcess.KEY,
                at.document(), at.employee(), new RoundNumber(at.round()), routing, fields, hash, summary));
    }

    public void approve() {
        status = MACHINE.require(status, TimesheetStatus.APPROVED);
        Snapshot at = snapshot();
        record("cost.timesheet.TimesheetApproved", envelope -> new TimesheetApproved(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round())));
    }

    public void reject(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.REJECTED);
        Snapshot at = snapshot();
        record("cost.timesheet.TimesheetRejected", envelope -> new TimesheetRejected(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    public void returnForRevision(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.RETURNED);
        Snapshot at = snapshot();
        record("cost.timesheet.TimesheetReturned", envelope -> new TimesheetReturned(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    public void cancelSubmission(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.DRAFT);
        Snapshot at = snapshot();
        record("cost.timesheet.TimesheetApprovalWithdrawn", envelope -> new TimesheetApprovalWithdrawn(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    /** What an event reports is fixed when it is recorded, not when it is published. */
    private record Snapshot(DocumentReference document, EmployeeId employee, Hours submittedHours,
                            LocalDate submittedOn, int round) {
    }

    private Snapshot snapshot() {
        return new Snapshot(document(), employee, submittedHours, submittedOn, round);
    }

    public boolean isAwaitingResultOfRound(int resultRound) {
        return status == TimesheetStatus.SUBMITTED && round == resultRound;
    }

    public ContentHash contentHash() {
        String content = workDate + "|" + submittedHours.value().toPlainString() + "|" + projectManager.value();
        try {
            return new ContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    public AccountingPeriod periodOfWork() {
        return AccountingPeriod.containing(workDate);
    }

    private void requireOwner(EmployeeId by) {
        if (!employee.equals(by)) {
            throw TimesheetError.COST_TIMESHEET_NOT_OWNER.refusal("Only the employee may change or submit the timesheet");
        }
    }

    private void record(String eventType, Function<EventEnvelope, DomainEvent> assemble) {
        record((envelopes, sequence) -> assemble.apply(envelopes.wrap(new EventDraft(eventType, 1, "Timesheet",
                timesheetId.value(), sequence, legalEntityId, workDate,
                touchedTogether))));
    }

    public DocumentReference document() {
        return new DocumentReference(TimesheetDocument.TYPE, new DocumentId(timesheetId.value()));
    }

    public TimesheetId timesheetId() {
        return timesheetId;
    }

    public DocumentNumber number() {
        return number;
    }

    public Optional<NumberingRuleVersionId> numberingRuleVersionId() {
        return Optional.ofNullable(numberingRuleVersionId);
    }

    public EmployeeId employee() {
        return employee;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public OrgUnitId orgUnitId() {
        return orgUnitId;
    }

    public LocalDate workDate() {
        return workDate;
    }

    public Hours submittedHours() {
        return submittedHours;
    }

    public EmployeeId projectManager() {
        return projectManager;
    }

    public TimesheetStatus status() {
        return status;
    }

    public int round() {
        return round;
    }

    public Optional<LocalDate> submittedOn() {
        return Optional.ofNullable(submittedOn);
    }

    public TimesheetStatus loadedStatus() {
        return loadedStatus;
    }

    public boolean isNew() {
        return isNew;
    }
}
