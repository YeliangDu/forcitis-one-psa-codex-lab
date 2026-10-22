package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Part of an {@link ApprovalRequest}: it changes only through the request, which records the events and owns the version. */
public final class ApprovalTask {

    public static final StateMachine<ApprovalTaskStatus> MACHINE = StateMachine.of(ApprovalTaskStatus.class)
            .allow(ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.COMPLETED, ApprovalTaskStatus.CLOSED)
            .allow(ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.PENDING, ApprovalTaskStatus.COMPLETED, ApprovalTaskStatus.CLOSED)
            .build();

    private final ApprovalTaskId taskId;
    private final ApprovalInstanceId instanceId;
    private final NodeKey nodeKey;
    private final NodeName nodeName;
    private final Instant createdAt;
    private final DeadlinePlan deadline;
    private final boolean isNew;
    private boolean changed;
    private ApprovalTaskStatus status;
    private UserId assignee;
    private Instant claimedAt;
    private Instant completedAt;
    private Instant overdueAt;
    private Instant escalatedAt;
    private TaskOutcome outcome;
    private TaskEffect effect;
    private Reason resultReason;
    private ContentHash approvedHash;

    private ApprovalTask(ApprovalTaskId taskId, ApprovalInstanceId instanceId, NodeKey nodeKey, NodeName nodeName, Instant createdAt,
            DeadlinePlan deadline, ApprovalTaskStatus status, boolean isNew) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
        this.nodeName = Objects.requireNonNull(nodeName, "nodeName");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.deadline = deadline;
        this.status = Objects.requireNonNull(status, "status");
        this.isNew = isNew;
        this.changed = isNew;
    }

    static ApprovalTask assigned(TaskAssignment assignment, Instant at) {
        return new ApprovalTask(assignment.taskId(), assignment.instanceId(), assignment.nodeKey(), assignment.nodeName(), at,
                assignment.deadline().orElse(null), ApprovalTaskStatus.PENDING, true);
    }

    static ApprovalTask passedBySystem(TaskAssignment assignment, UserId approver, Instant at) {
        ApprovalTask task = new ApprovalTask(assignment.taskId(), assignment.instanceId(), assignment.nodeKey(), assignment.nodeName(), at, null,
                ApprovalTaskStatus.COMPLETED, true);
        task.assignee = approver;
        task.completedAt = at;
        task.outcome = TaskOutcome.APPROVED;
        task.effect = TaskEffect.AUTOMATIC;
        return task;
    }

    public static ApprovalTask restore(ApprovalTaskId taskId, ApprovalInstanceId instanceId, NodeKey nodeKey, NodeName nodeName, ApprovalTaskStatus status,
            UserId assignee, Instant createdAt, DeadlinePlan deadline, Instant claimedAt, Instant completedAt, Instant overdueAt,
            Instant escalatedAt, TaskOutcome outcome, TaskEffect effect, Reason resultReason, ContentHash approvedHash) {
        ApprovalTask task = new ApprovalTask(taskId, instanceId, nodeKey, nodeName, createdAt, deadline, status, false);
        task.assignee = assignee;
        task.claimedAt = claimedAt;
        task.completedAt = completedAt;
        task.overdueAt = overdueAt;
        task.escalatedAt = escalatedAt;
        task.outcome = outcome;
        task.effect = effect;
        task.resultReason = resultReason;
        task.approvedHash = approvedHash;
        return task;
    }

    void claim(UserId by, Instant at) {
        move(ApprovalTaskStatus.CLAIMED);
        assignee = Objects.requireNonNull(by, "by");
        claimedAt = at;
    }

    void release(UserId by) {
        requireHeldBy(by, "release");
        move(ApprovalTaskStatus.PENDING);
        assignee = null;
        claimedAt = null;
    }

    void transfer(UserId from, UserId to) {
        requireHeldBy(from, "transfer");
        if (from.equals(to)) {
            throw illegal("transfer to the person who holds it");
        }
        assignee = Objects.requireNonNull(to, "to");
        changed = true;
    }

    void reassign(UserId to, Instant at) {
        if (!status.isOpen()) {
            throw illegal("reassign");
        }
        if (status == ApprovalTaskStatus.CLAIMED) {
            move(ApprovalTaskStatus.PENDING);
        }
        move(ApprovalTaskStatus.CLAIMED);
        assignee = Objects.requireNonNull(to, "to");
        claimedAt = at;
    }

    void complete(UserId by, TaskOutcome result, boolean advisory, Reason reason, ContentHash digest, Instant at) {
        requireHeldBy(by, "complete");
        move(ApprovalTaskStatus.COMPLETED);
        completedAt = at;
        outcome = Objects.requireNonNull(result, "result");
        effect = advisory ? TaskEffect.ADVISORY : TaskEffect.BINDING;
        resultReason = reason;
        if (result == TaskOutcome.APPROVED && !advisory) {
            approvedHash = Objects.requireNonNull(digest, "digest");
        }
    }

    void close() {
        move(ApprovalTaskStatus.CLOSED);
    }

    void markOverdue(Instant now) {
        if (!status.isOpen() || deadline == null || overdueAt != null || now.isBefore(deadline.remindAt())) {
            throw illegal("be marked overdue");
        }
        overdueAt = now;
        changed = true;
    }

    void escalate(Instant now) {
        if (!status.isOpen() || overdueAt == null || escalatedAt != null || deadline == null || now.isBefore(deadline.escalateAt())) {
            throw illegal("escalate");
        }
        escalatedAt = now;
        if (status == ApprovalTaskStatus.CLAIMED) {
            move(ApprovalTaskStatus.PENDING);
            assignee = null;
            claimedAt = null;
        }
        changed = true;
    }

    private void requireHeldBy(UserId user, String action) {
        if (status != ApprovalTaskStatus.CLAIMED || !assignee.equals(user)) {
            throw illegal(action);
        }
    }

    private void move(ApprovalTaskStatus target) {
        status = MACHINE.require(status, target);
        changed = true;
    }

    private com.forcitis.one.kernel.DomainException illegal(String action) {
        return KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("Task " + taskId.value() + " cannot " + action + " while " + status);
    }

    public ApprovalTaskId taskId() {
        return taskId;
    }

    public ApprovalInstanceId instanceId() {
        return instanceId;
    }

    public NodeKey nodeKey() {
        return nodeKey;
    }

    public NodeName nodeName() {
        return nodeName;
    }

    public ApprovalTaskStatus status() {
        return status;
    }

    public Optional<UserId> assignee() {
        return Optional.ofNullable(assignee);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<DeadlinePlan> deadline() {
        return Optional.ofNullable(deadline);
    }

    public Optional<Instant> claimedAt() {
        return Optional.ofNullable(claimedAt);
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<Instant> overdueAt() {
        return Optional.ofNullable(overdueAt);
    }

    public Optional<Instant> escalatedAt() {
        return Optional.ofNullable(escalatedAt);
    }

    public Optional<TaskOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public Optional<TaskEffect> effect() {
        return Optional.ofNullable(effect);
    }

    public Optional<Reason> resultReason() {
        return Optional.ofNullable(resultReason);
    }

    public Optional<ContentHash> approvedHash() {
        return Optional.ofNullable(approvedHash);
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean isChanged() {
        return changed;
    }
}
