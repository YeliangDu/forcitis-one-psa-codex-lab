package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskResulted;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** The engine task as the approval module sees it: the status and the event sequence live here, the candidates stay in the engine. */
public final class ApprovalTask {

    private static final String AGGREGATE_TYPE = "ApprovalTask";
    private static final String EVENT_PREFIX = "platform.approval.";

    private final ApprovalTaskId taskId;
    private final ApprovalInstanceId instanceId;
    private final ProcessKey processKey;
    private final DocumentReference document;
    private final LegalEntityId legalEntityId;
    private final String nodeKey;
    private final Instant dueAt;
    private final Instant escalateAt;
    private final long loadedVersion;
    private final ApprovalTaskStatus loadedStatus;
    private final boolean isNew;
    private ApprovalTaskStatus status;
    private UserId assignee;
    private Instant overdueAt;
    private Instant escalatedAt;
    private String approvedDigest;
    private long eventSequence;
    private final List<PendingEvent> events = new ArrayList<>();

    private ApprovalTask(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document,
            LegalEntityId legalEntityId, String nodeKey, ApprovalTaskStatus status, UserId assignee, Instant dueAt, Instant escalateAt,
            Instant overdueAt, Instant escalatedAt, String approvedDigest, long eventSequence, long version, boolean isNew) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.processKey = Objects.requireNonNull(processKey, "processKey");
        this.document = Objects.requireNonNull(document, "document");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
        this.status = Objects.requireNonNull(status, "status");
        this.assignee = assignee;
        this.dueAt = dueAt;
        this.escalateAt = escalateAt;
        this.overdueAt = overdueAt;
        this.escalatedAt = escalatedAt;
        this.approvedDigest = approvedDigest;
        this.eventSequence = eventSequence;
        this.loadedVersion = version;
        this.loadedStatus = status;
        this.isNew = isNew;
        if ((dueAt == null) != (escalateAt == null)) {
            throw new IllegalArgumentException("a deadline needs both the due and the escalation time");
        }
    }

    public static ApprovalTask assign(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String processName,
            DocumentReference document, LegalEntityId legalEntityId, String nodeKey, String nodeName, UserId initiator,
            List<UserId> candidates, Instant dueAt, Instant escalateAt, LocalDate businessDate) {
        ApprovalTask task = new ApprovalTask(taskId, instanceId, processKey, document, legalEntityId, nodeKey, ApprovalTaskStatus.PENDING,
                null, dueAt, escalateAt, null, null, null, 0, 0, true);
        List<UserId> offered = List.copyOf(candidates);
        task.record("TaskAssigned", businessDate, envelope -> new TaskAssigned(envelope, taskId, instanceId, processKey,
                processName, document, nodeKey, nodeName, initiator, offered));
        return task;
    }

    /** A repeat approver whose earlier approval still covers the same content: passed by the system, no card and no result event. */
    public static ApprovalTask assignAutomatically(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
            DocumentReference document, LegalEntityId legalEntityId, String nodeKey) {
        return new ApprovalTask(taskId, instanceId, processKey, document, legalEntityId, nodeKey, ApprovalTaskStatus.COMPLETED, null,
                null, null, null, null, null, 0, 0, true);
    }

    public static ApprovalTask restore(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
            DocumentReference document, LegalEntityId legalEntityId, String nodeKey, ApprovalTaskStatus status, UserId assignee,
            Instant dueAt, Instant escalateAt, Instant overdueAt, Instant escalatedAt, String approvedDigest, long eventSequence,
            long version) {
        return new ApprovalTask(taskId, instanceId, processKey, document, legalEntityId, nodeKey, status, assignee, dueAt, escalateAt,
                overdueAt, escalatedAt, approvedDigest, eventSequence, version, false);
    }

    public void claim(UserId by, LocalDate businessDate) {
        require(status == ApprovalTaskStatus.PENDING, "claim");
        status = ApprovalTaskStatus.CLAIMED;
        assignee = Objects.requireNonNull(by, "by");
        record("TaskClaimed", businessDate, envelope -> new TaskClaimed(envelope, taskId, instanceId, document, by));
    }

    public void complete(UserId by, TaskOutcome outcome, boolean advisory, String reason, String contentDigest, LocalDate businessDate) {
        require(status == ApprovalTaskStatus.CLAIMED && assignee.equals(by), "complete");
        status = ApprovalTaskStatus.COMPLETED;
        if (outcome == TaskOutcome.APPROVED && !advisory) {
            approvedDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        }
        record("TaskResulted", businessDate, envelope -> new TaskResulted(envelope, taskId, instanceId, processKey, document,
                nodeKey, by, outcome, advisory, reason));
    }

    public void completeAutomatically() {
        require(status == ApprovalTaskStatus.PENDING, "complete automatically");
        status = ApprovalTaskStatus.COMPLETED;
    }

    public void close() {
        require(status.isOpen(), "close");
        status = ApprovalTaskStatus.CLOSED;
    }

    public void transfer(UserId from, UserId to, LocalDate businessDate) {
        require(status == ApprovalTaskStatus.CLAIMED && assignee.equals(from) && !from.equals(to), "transfer");
        assignee = Objects.requireNonNull(to, "to");
        record("TaskTransferred", businessDate, envelope -> new TaskTransferred(envelope, taskId, instanceId, document,
                nodeKey, from, to));
    }

    public void markOverdue(List<UserId> holders, Instant now, LocalDate businessDate) {
        require(status.isOpen() && dueAt != null && overdueAt == null && !now.isBefore(dueAt), "mark overdue");
        overdueAt = now;
        List<UserId> reminded = List.copyOf(holders);
        Instant due = dueAt;
        record("ApprovalTaskOverdue", businessDate, envelope -> new ApprovalTaskOverdue(envelope, taskId, instanceId,
                document, nodeKey, due, reminded));
    }

    /** A claimed task goes back to the pool so that the people it was escalated to can take it. */
    public void escalate(List<UserId> escalatedTo, boolean toProcessAdministrator, Instant now, LocalDate businessDate) {
        require(status.isOpen() && overdueAt != null && escalatedAt == null && escalateAt != null && !now.isBefore(escalateAt),
                "escalate");
        escalatedAt = now;
        if (status == ApprovalTaskStatus.CLAIMED) {
            status = ApprovalTaskStatus.PENDING;
            assignee = null;
        }
        List<UserId> receivers = List.copyOf(escalatedTo);
        record("ApprovalTaskEscalated", businessDate, envelope -> new ApprovalTaskEscalated(envelope, taskId, instanceId,
                document, nodeKey, receivers, toProcessAdministrator));
    }

    private void require(boolean legal, String action) {
        if (!legal) {
            throw new ApprovalTransitionException(ApprovalTransitionException.Violation.ILLEGAL_TRANSITION,
                    "Task " + taskId.value() + " cannot " + action + " while " + status);
        }
    }

    private void record(String eventName, LocalDate businessDate, Function<EventEnvelope, DomainEvent> assemble) {
        EventDraft draft = new EventDraft(EVENT_PREFIX + eventName, 1, AGGREGATE_TYPE, taskId.value(), ++eventSequence, legalEntityId,
                businessDate, new EventSource(SourceSystem.ONE, null), List.of());
        events.add(new PendingEvent(draft, assemble));
    }

    public List<PendingEvent> pullEvents() {
        List<PendingEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public ApprovalTaskId taskId() {
        return taskId;
    }

    public ApprovalInstanceId instanceId() {
        return instanceId;
    }

    public ProcessKey processKey() {
        return processKey;
    }

    public DocumentReference document() {
        return document;
    }

    public LegalEntityId legalEntityId() {
        return legalEntityId;
    }

    public String nodeKey() {
        return nodeKey;
    }

    public ApprovalTaskStatus status() {
        return status;
    }

    public Optional<UserId> assignee() {
        return Optional.ofNullable(assignee);
    }

    public Optional<Instant> dueAt() {
        return Optional.ofNullable(dueAt);
    }

    public Optional<Instant> escalateAt() {
        return Optional.ofNullable(escalateAt);
    }

    public Optional<Instant> overdueAt() {
        return Optional.ofNullable(overdueAt);
    }

    public Optional<Instant> escalatedAt() {
        return Optional.ofNullable(escalatedAt);
    }

    public Optional<String> approvedDigest() {
        return Optional.ofNullable(approvedDigest);
    }

    public long eventSequence() {
        return eventSequence;
    }

    public long loadedVersion() {
        return loadedVersion;
    }

    public ApprovalTaskStatus loadedStatus() {
        return loadedStatus;
    }

    public boolean isNew() {
        return isNew;
    }
}
