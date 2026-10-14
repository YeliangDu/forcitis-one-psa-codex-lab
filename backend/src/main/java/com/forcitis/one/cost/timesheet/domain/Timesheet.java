package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetCancelled;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A timesheet moves DRAFT, SUBMITTED, then APPROVED or REJECTED; a return gives it back to its owner for changes (RETURNED) and a cancel
 * gives it back as a draft. The approval module owns the process state, this aggregate owns the timesheet's state, and only events join
 * them. The work date is what the employee entered and never changes afterwards; the day of submission is a separate fact.
 */
public final class Timesheet extends AggregateRoot {

    public static final StateMachine<TimesheetStatus> MACHINE = StateMachine.of(TimesheetStatus.class)
            .allow(TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED)
            .allow(TimesheetStatus.RETURNED, TimesheetStatus.SUBMITTED)
            .allow(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED, TimesheetStatus.RETURNED,
                    TimesheetStatus.DRAFT)
            .build();

    private static final String EVENT_PREFIX = "cost.timesheet.";

    private final TimesheetId timesheetId;
    private final DocumentNumber number;
    private final UserId employee;
    private final LegalEntityId legalEntityId;
    private final OrgUnitId orgUnitId;
    private final LocalDate workDate;
    private final TimesheetStatus loadedStatus;
    private final boolean isNew;
    private final List<AggregateReference> touchedTogether;
    private Hours submittedHours;
    private UserId projectManager;
    private TimesheetStatus status;
    private int round;
    private LocalDate submittedOn;

    private Timesheet(TimesheetId timesheetId, DocumentNumber number, UserId employee, LegalEntityId legalEntityId, OrgUnitId orgUnitId,
            LocalDate workDate, Hours submittedHours, UserId projectManager, TimesheetStatus status,
            int round, LocalDate submittedOn, List<AggregateReference> touchedTogether, long version, long eventSequence, boolean isNew) {
        super(version, eventSequence);
        this.timesheetId = Objects.requireNonNull(timesheetId, "timesheetId");
        this.number = Objects.requireNonNull(number, "number");
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
     * unit of work name it as a related aggregate.
     */
    public static Timesheet draft(TimesheetId timesheetId, DocumentNumber number, AggregateReference counter, UserId employee,
            LegalEntityId legalEntityId, OrgUnitId orgUnitId, LocalDate workDate, Hours submittedHours,
            UserId projectManager, LocalDate today) {
        if (workDate.isAfter(today)) {
            throw TimesheetError.COST_TIMESHEET_WORK_DATE_IN_FUTURE.refusal("The work date " + workDate + " is after today, " + today);
        }
        return new Timesheet(timesheetId, number, employee, legalEntityId, orgUnitId, workDate, submittedHours,
                projectManager, TimesheetStatus.DRAFT, 0, null, List.of(counter), 0, 0, true);
    }

    public static Timesheet restore(TimesheetId timesheetId, DocumentNumber number, UserId employee, LegalEntityId legalEntityId,
            OrgUnitId orgUnitId, LocalDate workDate, Hours submittedHours, UserId projectManager,
            TimesheetStatus status, int round, LocalDate submittedOn, long version, long eventSequence) {
        return new Timesheet(timesheetId, number, employee, legalEntityId, orgUnitId, workDate, submittedHours,
                projectManager, status, round, submittedOn, List.of(), version, eventSequence, false);
    }

    public void revise(UserId by, LocalDate newWorkDate, Hours newHours, UserId newProjectManager) {
        requireOwner(by);
        if (!status.isEditable()) {
            throw TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL.refusal("The timesheet cannot be changed while it is " + status);
        }
        if (!workDate.equals(newWorkDate)) {
            throw TimesheetError.COST_TIMESHEET_WORK_DATE_FIXED.refusal(
                    "The work date " + workDate + " was entered with the timesheet and is not rewritten by a resubmission");
        }
        submittedHours = Objects.requireNonNull(newHours, "newHours");
        projectManager = Objects.requireNonNull(newProjectManager, "newProjectManager");
    }

    public void submit(UserId by, LocalDate today) {
        requireOwner(by);
        status = MACHINE.require(status, TimesheetStatus.SUBMITTED, TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL);
        round++;
        submittedOn = Objects.requireNonNull(today, "today");
        Snapshot at = snapshot();
        List<TimesheetRoutingFlag> flags = List.of(TimesheetRoutingFlag.PROJECT_HOURS);
        ContentHash hash = contentHash();
        ApprovalSummary summary = new ApprovalSummary(number.value() + " " + workDate + " " + submittedHours.value().toPlainString() + "h");
        record("TimesheetSubmitted", envelope -> new TimesheetSubmitted(envelope, at.document(), number, at.employee(), projectManager,
                at.submittedHours(), at.submittedOn(), new RoundNumber(at.round()), flags, hash, summary));
    }

    public void approve() {
        status = MACHINE.require(status, TimesheetStatus.APPROVED, TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL);
        Snapshot at = snapshot();
        record("TimesheetApproved", envelope -> new TimesheetApproved(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round())));
    }

    public void reject(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.REJECTED, TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL);
        Snapshot at = snapshot();
        record("TimesheetRejected", envelope -> new TimesheetRejected(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    public void returnForRevision(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.RETURNED, TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL);
        Snapshot at = snapshot();
        record("TimesheetReturned", envelope -> new TimesheetReturned(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    public void cancelSubmission(Reason reason) {
        status = MACHINE.require(status, TimesheetStatus.DRAFT, TimesheetError.COST_TIMESHEET_TRANSITION_ILLEGAL);
        Snapshot at = snapshot();
        record("TimesheetCancelled", envelope -> new TimesheetCancelled(envelope, at.document(), at.employee(),
                at.submittedHours(), new RoundNumber(at.round()), reason));
    }

    /** What an event reports is fixed when it is recorded, not when it is published. */
    private record Snapshot(DocumentReference document, UserId employee, Hours submittedHours,
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

    private void requireOwner(UserId by) {
        if (!employee.equals(by)) {
            throw TimesheetError.COST_TIMESHEET_NOT_OWNER.refusal("Only the employee may change or submit the timesheet");
        }
    }

    private void record(String eventName, Function<EventEnvelope, DomainEvent> assemble) {
        record((envelopes, sequence) -> assemble.apply(envelopes.wrap(new EventDraft(EVENT_PREFIX + eventName, 1, "Timesheet",
                String.valueOf(timesheetId.value()), sequence, legalEntityId, workDate, new EventSource(SourceSystem.ONE, null),
                touchedTogether))));
    }

    public DocumentReference document() {
        return new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(timesheetId.value())));
    }

    public TimesheetId timesheetId() {
        return timesheetId;
    }

    public DocumentNumber number() {
        return number;
    }

    public UserId employee() {
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

    public UserId projectManager() {
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
