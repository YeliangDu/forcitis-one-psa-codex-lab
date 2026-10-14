package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetCancelled;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A timesheet moves DRAFT, SUBMITTED, then APPROVED or REJECTED; a return gives it back to its owner for changes (RETURNED) and a cancel
 * gives it back as a draft. The approval module owns the process state, this aggregate owns the timesheet's state, and only events join them.
 */
public final class Timesheet {

    public enum Action {
        REVISE,
        SUBMIT,
        APPROVE,
        REJECT,
        RETURN,
        CANCEL
    }

    private static final String EVENT_PREFIX = "cost.timesheet.";
    private static final Map<Action, Map<TimesheetStatus, TimesheetStatus>> TRANSITIONS = new EnumMap<>(Action.class);

    static {
        allow(Action.REVISE, null, TimesheetStatus.DRAFT, TimesheetStatus.RETURNED);
        allow(Action.SUBMIT, TimesheetStatus.SUBMITTED, TimesheetStatus.DRAFT, TimesheetStatus.RETURNED);
        allow(Action.APPROVE, TimesheetStatus.APPROVED, TimesheetStatus.SUBMITTED);
        allow(Action.REJECT, TimesheetStatus.REJECTED, TimesheetStatus.SUBMITTED);
        allow(Action.RETURN, TimesheetStatus.RETURNED, TimesheetStatus.SUBMITTED);
        allow(Action.CANCEL, TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED);
    }

    private static void allow(Action action, TimesheetStatus target, TimesheetStatus... from) {
        Map<TimesheetStatus, TimesheetStatus> row = new EnumMap<>(TimesheetStatus.class);
        for (TimesheetStatus status : from) {
            row.put(status, target == null ? status : target);
        }
        TRANSITIONS.put(action, row);
    }

    private final TimesheetId timesheetId;
    private final UserId employee;
    private final LegalEntityId legalEntityId;
    private final long loadedVersion;
    private final TimesheetStatus loadedStatus;
    private final boolean isNew;
    private WorkPackageId workPackageId;
    private LocalDate workDate;
    private Hours submittedHours;
    private UserId projectManager;
    private TimesheetStatus status;
    private int round;
    private ApprovalInstanceId approvalInstanceId;
    private long eventSequence;
    private final List<PendingEvent> events = new ArrayList<>();

    private Timesheet(TimesheetId timesheetId, UserId employee, LegalEntityId legalEntityId, WorkPackageId workPackageId,
            LocalDate workDate, Hours submittedHours, UserId projectManager, TimesheetStatus status, int round,
            ApprovalInstanceId approvalInstanceId, long eventSequence, long version, boolean isNew) {
        this.timesheetId = Objects.requireNonNull(timesheetId, "timesheetId");
        this.employee = Objects.requireNonNull(employee, "employee");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.workPackageId = Objects.requireNonNull(workPackageId, "workPackageId");
        this.workDate = Objects.requireNonNull(workDate, "workDate");
        this.submittedHours = Objects.requireNonNull(submittedHours, "submittedHours");
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager");
        this.status = Objects.requireNonNull(status, "status");
        this.round = round;
        this.approvalInstanceId = approvalInstanceId;
        this.eventSequence = eventSequence;
        this.loadedVersion = version;
        this.loadedStatus = status;
        this.isNew = isNew;
    }

    public static Timesheet draft(TimesheetId timesheetId, UserId employee, LegalEntityId legalEntityId, WorkPackageId workPackageId,
            LocalDate workDate, Hours submittedHours, UserId projectManager) {
        return new Timesheet(timesheetId, employee, legalEntityId, workPackageId, workDate, submittedHours, projectManager,
                TimesheetStatus.DRAFT, 0, null, 0, 0, true);
    }

    public static Timesheet restore(TimesheetId timesheetId, UserId employee, LegalEntityId legalEntityId, WorkPackageId workPackageId,
            LocalDate workDate, Hours submittedHours, UserId projectManager, TimesheetStatus status, int round,
            ApprovalInstanceId approvalInstanceId, long eventSequence, long version) {
        return new Timesheet(timesheetId, employee, legalEntityId, workPackageId, workDate, submittedHours, projectManager, status,
                round, approvalInstanceId, eventSequence, version, false);
    }

    public void revise(UserId by, WorkPackageId newWorkPackage, LocalDate newWorkDate, Hours newHours, UserId newProjectManager) {
        target(Action.REVISE);
        requireOwner(by);
        workPackageId = Objects.requireNonNull(newWorkPackage, "newWorkPackage");
        workDate = Objects.requireNonNull(newWorkDate, "newWorkDate");
        submittedHours = Objects.requireNonNull(newHours, "newHours");
        projectManager = Objects.requireNonNull(newProjectManager, "newProjectManager");
    }

    public void submit(UserId by, ApprovalInstanceId instance) {
        TimesheetStatus target = target(Action.SUBMIT);
        requireOwner(by);
        status = target;
        round++;
        approvalInstanceId = Objects.requireNonNull(instance, "instance");
        Snapshot at = snapshot();
        record("TimesheetSubmitted", envelope -> new TimesheetSubmitted(envelope, at.document(), at.employee(), instance, at.workPackageId(),
                at.submittedHours(), at.round()));
    }

    public void approve() {
        status = target(Action.APPROVE);
        Snapshot at = snapshot();
        record("TimesheetApproved", envelope -> new TimesheetApproved(envelope, at.document(), at.employee(), at.workPackageId(),
                at.submittedHours(), at.round()));
    }

    public void reject(String reason) {
        status = target(Action.REJECT);
        Snapshot at = snapshot();
        record("TimesheetRejected", envelope -> new TimesheetRejected(envelope, at.document(), at.employee(), at.workPackageId(),
                at.submittedHours(), at.round(), reason));
    }

    public void returnForRevision(String reason) {
        status = target(Action.RETURN);
        Snapshot at = snapshot();
        record("TimesheetReturned", envelope -> new TimesheetReturned(envelope, at.document(), at.employee(), at.workPackageId(),
                at.submittedHours(), at.round(), reason));
    }

    public void cancelSubmission(String reason) {
        status = target(Action.CANCEL);
        Snapshot at = snapshot();
        record("TimesheetCancelled", envelope -> new TimesheetCancelled(envelope, at.document(), at.employee(), at.workPackageId(),
                at.submittedHours(), at.round(), reason));
    }

    /** What an event reports is fixed when it is recorded, not when it is published. */
    private record Snapshot(DocumentReference document, UserId employee, WorkPackageId workPackageId, Hours submittedHours, int round) {
    }

    private Snapshot snapshot() {
        return new Snapshot(document(), employee, workPackageId, submittedHours, round);
    }

    public boolean isAwaitingResultOfRound(int resultRound) {
        return status == TimesheetStatus.SUBMITTED && round == resultRound;
    }

    public String contentDigest() {
        String content = workPackageId.value() + "|" + workDate + "|" + submittedHours.value().toPlainString() + "|" + projectManager.value();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private TimesheetStatus target(Action action) {
        TimesheetStatus target = TRANSITIONS.get(action).get(status);
        if (target == null) {
            throw new TimesheetTransitionException(TimesheetTransitionException.Violation.ILLEGAL_TRANSITION,
                    action + " is not possible while the timesheet is " + status);
        }
        return target;
    }

    private void requireOwner(UserId by) {
        if (!employee.equals(by)) {
            throw new TimesheetTransitionException(TimesheetTransitionException.Violation.NOT_OWNER, "Only the employee may change or submit the timesheet");
        }
    }

    private void record(String eventName, Function<EventEnvelope, DomainEvent> assemble) {
        EventDraft draft = new EventDraft(EVENT_PREFIX + eventName, 1, "Timesheet", String.valueOf(timesheetId.value()), ++eventSequence,
                legalEntityId, workDate, new EventSource(SourceSystem.ONE, null), List.of());
        events.add(new PendingEvent(draft, assemble));
    }

    public DocumentReference document() {
        return new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(timesheetId.value())));
    }

    public List<PendingEvent> pullEvents() {
        List<PendingEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public TimesheetId timesheetId() {
        return timesheetId;
    }

    public UserId employee() {
        return employee;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public WorkPackageId workPackageId() {
        return workPackageId;
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

    public Optional<ApprovalInstanceId> approvalInstanceId() {
        return Optional.ofNullable(approvalInstanceId);
    }

    public long eventSequence() {
        return eventSequence;
    }

    public long loadedVersion() {
        return loadedVersion;
    }

    public TimesheetStatus loadedStatus() {
        return loadedStatus;
    }

    public boolean isNew() {
        return isNew;
    }
}
