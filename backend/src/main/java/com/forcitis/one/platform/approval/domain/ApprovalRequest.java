package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalRequestId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ReturnSource;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.platform.approval.api.TaskRecordId;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.ApprovalRoundStarted;
import com.forcitis.one.platform.approval.events.ApprovalTaskBecameOverdue;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskClosed;
import com.forcitis.one.platform.approval.events.TaskHandled;
import com.forcitis.one.platform.approval.events.TaskReassigned;
import com.forcitis.one.platform.approval.events.TaskReleased;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * One request per (process type, document). Each round has its own engine instance, and the tasks of the round belong to the
 * request: they change only through the methods below, under the request's version, and their events carry the request's event
 * sequence.
 */
public final class ApprovalRequest extends AggregateRoot {

    private static final String AGGREGATE_TYPE = "ApprovalRequest";
    private static final String EVENT_PREFIX = "platform.approval.";

    public static final StateMachine<ApprovalStatus> MACHINE = StateMachine.of(ApprovalStatus.class)
            .allow(ApprovalStatus.RUNNING, ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.RETURNED,
                    ApprovalStatus.CANCELLED)
            .allow(ApprovalStatus.RETURNED, ApprovalStatus.RUNNING)
            .allow(ApprovalStatus.REJECTED, ApprovalStatus.RUNNING)
            .allow(ApprovalStatus.CANCELLED, ApprovalStatus.RUNNING)
            .build();

    private final ApprovalRequestId id;
    private final IdGenerator ids;
    private final ProcessKey processKey;
    private final DocumentReference document;
    private final LegalEntityId legalEntityId;
    private final EmployeeId initiator;
    private final ApprovalStatus loadedStatus;
    private final int loadedRound;
    private final boolean isNew;
    private final List<ApprovalTask> tasks;
    private final List<TaskHandling> handlings = new ArrayList<>();
    private int round;
    private ApprovalInstanceId instanceId;
    private ApprovalStatus status;
    private @Nullable Reason resultReason;
    private ApprovalSummary summary;
    private @Nullable UUID trigger;

    private ApprovalRequest(ApprovalRequestId id, IdGenerator ids, ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId,
            EmployeeId initiator, int round, ApprovalInstanceId instanceId, ApprovalStatus status, @Nullable Reason resultReason, ApprovalSummary summary,
            List<ApprovalTask> tasks, @Nullable UUID trigger, long version, long eventSequence, boolean isNew) {
        super(version, eventSequence);
        this.id = Objects.requireNonNull(id, "id");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.processKey = Objects.requireNonNull(processKey, "processKey");
        this.document = Objects.requireNonNull(document, "document");
        this.legalEntityId = Objects.requireNonNull(legalEntityId, "legalEntityId");
        this.initiator = Objects.requireNonNull(initiator, "initiator");
        this.round = round;
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.status = Objects.requireNonNull(status, "status");
        this.resultReason = resultReason;
        this.summary = Objects.requireNonNull(summary, "summary");
        this.tasks = new ArrayList<>(tasks);
        this.trigger = trigger;
        this.loadedStatus = status;
        this.loadedRound = round;
        this.isNew = isNew;
    }

    public static ApprovalRequest begin(IdGenerator ids, ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId,
            EmployeeId initiator, ApprovalInstanceId instanceId, ApprovalSummary summary, UUID trigger, Moment now) {
        ApprovalRequest request = new ApprovalRequest(new ApprovalRequestId(ids.next()), ids, processKey, document, legalEntityId, initiator, 1, instanceId, ApprovalStatus.RUNNING,
                null, summary, List.of(), Objects.requireNonNull(trigger, "trigger"), 0, 0, true);
        request.announceRound(now);
        return request;
    }

    public static ApprovalRequest restore(ApprovalRequestId id, IdGenerator ids, ProcessKey processKey, DocumentReference document,
            LegalEntityId legalEntityId, EmployeeId initiator, int round, ApprovalInstanceId instanceId, ApprovalStatus status, @Nullable Reason resultReason,
            ApprovalSummary summary, List<ApprovalTask> tasks, long version, long eventSequence) {
        return new ApprovalRequest(id, ids, processKey, document, legalEntityId, initiator, round, instanceId, status, resultReason, summary, tasks,
                null, version, eventSequence, false);
    }

    public void restart(EmployeeId by, ApprovalInstanceId newInstance, ApprovalSummary newSummary, UUID newTrigger, Moment now) {
        if (!initiator.equals(by)) {
            throw ApprovalError.PLATFORM_APPROVAL_NOT_INITIATOR.refusal("Only the initiator of the previous round may start the next one");
        }
        status = MACHINE.require(status, ApprovalStatus.RUNNING);
        round++;
        instanceId = Objects.requireNonNull(newInstance, "newInstance");
        trigger = Objects.requireNonNull(newTrigger, "newTrigger");
        summary = Objects.requireNonNull(newSummary, "newSummary");
        resultReason = null;
        announceRound(now);
    }

    private void announceRound(Moment now) {
        ProcessKey key = processKey;
        ApprovalInstanceId instance = instanceId;
        RoundNumber started = new RoundNumber(round);
        emit("ApprovalRoundStarted", now, envelope -> new ApprovalRoundStarted(envelope, document, key, instance, started, initiator));
    }

    public void assignTask(TaskAssignment assignment, Moment now) {
        requireOpenRound(assignment);
        tasks.add(ApprovalTask.assigned(assignment, now.at()));
        emit("TaskAssigned", now, envelope -> new TaskAssigned(envelope, assignment.taskId(), assignment.instanceId(), processKey,
                assignment.processName(), document, summary, assignment.nodeKey(), assignment.nodeName(), initiator,
                assignment.candidates(), assignment.deadline().map(DeadlinePlan::dueAt).orElse(null)));
    }

    public boolean isRepeatApprover(NodeKey node, UserId user, ContentHash digest) {
        return tasks.stream().anyMatch(task -> task.instanceId().equals(instanceId) && task.nodeKey().equals(node)
                && task.status() == ApprovalTaskStatus.COMPLETED && task.assignee().map(user::equals).orElse(false)
                && task.approvedHash().map(digest::equals).orElse(false));
    }

    /**
     * Whether the user already decided a task of the node of this task in the same round (the same instance). A node with several tasks is a countersign:
     * one person does not decide two of them, so a task is not handed to such a person.
     */
    public boolean hasDecidedOnNodeOf(ApprovalTaskId taskId, UserId user) {
        ApprovalTask task = task(taskId);
        return tasks.stream().anyMatch(decided -> decided.instanceId().equals(task.instanceId()) && decided.nodeKey().equals(task.nodeKey())
                && decided.status() == ApprovalTaskStatus.COMPLETED && decided.assignee().map(user::equals).orElse(false));
    }

    public void passAutomatically(TaskAssignment assignment, UserId approver, Moment now) {
        requireOpenRound(assignment);
        ApprovalTask passed = ApprovalTask.passedBySystem(assignment, approver, now.at());
        tasks.add(passed);
        note(passed, TaskRecordAction.PASSED_AUTOMATICALLY, approver, null, null, null, now);
        emitResult(assignment.taskId(), assignment.instanceId(), assignment.nodeKey(), approver, TaskOutcome.APPROVED, TaskEffect.AUTOMATIC,
                null, now);
    }

    public void claimTask(ApprovalTaskId taskId, UserId by, Moment now) {
        ApprovalTask task = task(taskId);
        task.claim(by, now.at());
        note(task, TaskRecordAction.CLAIMED, by, null, null, null, now);
        ApprovalInstanceId instance = task.instanceId();
        emit("TaskClaimed", now, envelope -> new TaskClaimed(envelope, taskId, instance, document, by));
    }

    public void releaseTask(ApprovalTaskId taskId, UserId by, Moment now) {
        ApprovalTask task = task(taskId);
        task.release(by);
        note(task, TaskRecordAction.RELEASED, by, null, null, null, now);
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("TaskReleased", now, envelope -> new TaskReleased(envelope, taskId, instance, document, node, by));
    }

    public void transferTask(ApprovalTaskId taskId, UserId from, UserId to, Reason reason, Moment now) {
        requireReason(reason, "A transfer");
        ApprovalTask task = task(taskId);
        task.transfer(from, to);
        note(task, TaskRecordAction.TRANSFERRED, from, null, to, reason, now);
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("TaskTransferred", now, envelope -> new TaskTransferred(envelope, taskId, instance, document, node, from, to, reason));
    }

    public void reassignTask(ApprovalTaskId taskId, UserId by, UserId to, Reason reason, Moment now) {
        requireReason(reason, "A reassignment");
        ApprovalTask task = task(taskId);
        @Nullable UserId previous = task.assignee().orElse(null);
        task.reassign(to, now.at());
        note(task, TaskRecordAction.REASSIGNED, by, previous, to, reason, now);
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("TaskReassigned", now, envelope -> new TaskReassigned(envelope, taskId, instance, document, node, previous, to, reason));
    }

    public void completeTask(ApprovalTaskId taskId, UserId by, TaskOutcome outcome, boolean advisory, @Nullable Reason reason,
            @Nullable ContentHash digest, Moment now) {
        if (outcome != TaskOutcome.APPROVED) {
            requireReason(reason, "A rejection or a return");
        }
        ApprovalTask task = task(taskId);
        task.complete(by, outcome, advisory, reason, digest, now.at());
        note(task, advisory ? TaskRecordAction.ADVISED : TaskRecordAction.valueOf(outcome.name()), by, null, null, reason, now);
        emitResult(taskId, task.instanceId(), task.nodeKey(), by, outcome, advisory ? TaskEffect.ADVISORY : TaskEffect.BINDING, reason, now);
    }

    public void markTaskOverdue(ApprovalTaskId taskId, List<UserId> holders, Moment now) {
        ApprovalTask task = task(taskId);
        task.markOverdue(now.at());
        List<UserId> reminded = List.copyOf(holders);
        Instant due = task.deadline().orElseThrow().dueAt();
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("ApprovalTaskBecameOverdue", now, envelope -> new ApprovalTaskBecameOverdue(envelope, taskId, instance, document, node, due, reminded));
    }

    public void escalateTask(ApprovalTaskId taskId, List<UserId> escalatedTo, boolean toProcessAdministrator, Moment now) {
        ApprovalTask task = task(taskId);
        task.escalate(now.at());
        List<UserId> receivers = List.copyOf(escalatedTo);
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("ApprovalTaskEscalated", now, envelope -> new ApprovalTaskEscalated(envelope, taskId, instance, document, node, receivers,
                toProcessAdministrator ? EscalationTarget.PROCESS_ADMINISTRATOR : EscalationTarget.SUPERVISOR));
    }

    public void closeTask(ApprovalTaskId taskId, Moment now) {
        closeOne(task(taskId), now);
    }

    public void approve(Moment now) {
        end(ApprovalStatus.APPROVED, ApprovalOutcome.APPROVED, null, null, now);
    }

    public void reject(Reason reason, Moment now) {
        end(ApprovalStatus.REJECTED, ApprovalOutcome.REJECTED, reason, null, now);
    }

    public void returnRound(Reason reason, Moment now) {
        end(ApprovalStatus.RETURNED, ApprovalOutcome.RETURNED, reason, ReturnSource.APPROVER, now);
    }

    public void returnRoundBySystem(Reason reason, Moment now) {
        end(ApprovalStatus.RETURNED, ApprovalOutcome.RETURNED, reason, ReturnSource.SYSTEM, now);
    }

    public WithdrawCheck checkWithdraw(EmployeeId actor, boolean cutoffPassed) {
        if (status != ApprovalStatus.RUNNING) {
            return WithdrawCheck.NOT_RUNNING;
        }
        if (!initiator.equals(actor)) {
            return WithdrawCheck.NOT_INITIATOR;
        }
        return cutoffPassed ? WithdrawCheck.CUTOFF_PASSED : WithdrawCheck.ALLOWED;
    }

    public void withdraw(EmployeeId actor, boolean cutoffPassed, Reason reason, Moment now) {
        requireWithdrawable(actor, cutoffPassed, reason);
        end(ApprovalStatus.RETURNED, ApprovalOutcome.RETURNED, reason, ReturnSource.INITIATOR, now);
    }

    public void cancel(EmployeeId actor, boolean cutoffPassed, Reason reason, Moment now) {
        requireWithdrawable(actor, cutoffPassed, reason);
        end(ApprovalStatus.CANCELLED, ApprovalOutcome.CANCELLED, reason, null, now);
    }

    private void requireWithdrawable(EmployeeId actor, boolean cutoffPassed, @Nullable Reason reason) {
        switch (checkWithdraw(actor, cutoffPassed)) {
            case NOT_INITIATOR -> throw ApprovalError.PLATFORM_APPROVAL_NOT_INITIATOR.refusal("Only the initiator may withdraw or cancel");
            case CUTOFF_PASSED -> throw ApprovalError.PLATFORM_APPROVAL_CUTOFF_PASSED.refusal("A cut-off node has already finished a task");
            case NOT_RUNNING -> throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal(
                    "The approval cannot be withdrawn or cancelled while it is " + status);
            case ALLOWED -> {
            }
        }
        requireReason(reason, "A withdrawal or cancellation");
    }

    private void end(ApprovalStatus target, ApprovalOutcome outcome, @Nullable Reason reason, @Nullable ReturnSource source, Moment now) {
        if (target != ApprovalStatus.APPROVED) {
            requireReason(reason, "A rejection, a return or a cancellation");
        }
        status = MACHINE.require(status, target);
        for (ApprovalTask open : openTasks()) {
            closeOne(open, now);
        }
        resultReason = reason;
        ApprovalInstanceId instance = instanceId;
        RoundNumber completed = new RoundNumber(round);
        emit("ApprovalCompleted", now, envelope -> new ApprovalCompleted(envelope, document, processKey, instance, completed, initiator,
                outcome, reason, source));
    }

    private void closeOne(ApprovalTask task, Moment now) {
        task.close();
        ApprovalTaskId id = task.taskId();
        ApprovalInstanceId instance = task.instanceId();
        NodeKey node = task.nodeKey();
        emit("TaskClosed", now, envelope -> new TaskClosed(envelope, id, instance, document, node));
    }

    private void note(ApprovalTask task, TaskRecordAction action, UserId actor, @Nullable UserId onBehalfOf, @Nullable UserId assignee, @Nullable Reason reason, Moment now) {
        handlings.add(new TaskHandling(new TaskRecordId(ids.next()), processKey, document, round, task.taskId(),
                task.instanceId(), task.nodeKey(), task.nodeName(), action, actor, Optional.ofNullable(onBehalfOf), Optional.ofNullable(assignee),
                Optional.ofNullable(reason), now.at()));
    }

    /** What the repository appends to the trail in the save that follows; asking twice gives nothing the second time. */
    public List<TaskHandling> drainHandlings() {
        List<TaskHandling> drained = List.copyOf(handlings);
        handlings.clear();
        return drained;
    }

    private void emitResult(ApprovalTaskId taskId, ApprovalInstanceId instance, NodeKey node, UserId by, TaskOutcome outcome,
            TaskEffect effect, @Nullable Reason reason, Moment now) {
        emit("TaskHandled", now, envelope -> new TaskHandled(envelope, taskId, instance, processKey, document, node, by, outcome,
                effect, reason));
    }

    private void emit(String eventName, Moment now, Function<EventEnvelope, DomainEvent> assemble) {
        record((envelopes, sequence) -> assemble.apply(envelopes.wrap(new EventDraft(EVENT_PREFIX + eventName, 1, AGGREGATE_TYPE,
                id.value(), sequence, legalEntityId, now.date(), List.of()))));
    }

    private void requireOpenRound(TaskAssignment assignment) {
        if (status != ApprovalStatus.RUNNING || !assignment.instanceId().equals(instanceId)) {
            throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal(
                    "Task " + assignment.taskId().value() + " does not belong to the running round of " + id.value());
        }
        if (tasks.stream().anyMatch(task -> task.taskId().equals(assignment.taskId()))) {
            throw KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("Task " + assignment.taskId().value() + " exists already");
        }
    }

    private static void requireReason(@Nullable Reason reason, String what) {
        if (reason == null) {
            throw KernelError.KERNEL_REASON_REQUIRED.refusal(what + " needs a reason");
        }
    }

    public ApprovalTask task(ApprovalTaskId taskId) {
        return tasks.stream().filter(task -> task.taskId().equals(taskId)).findFirst().orElseThrow(
                () -> ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND.refusal("Task " + taskId.value() + " is not part of " + id.value()));
    }

    public Optional<ApprovalTask> findTask(ApprovalTaskId taskId) {
        return tasks.stream().filter(task -> task.taskId().equals(taskId)).findFirst();
    }

    public List<ApprovalTask> tasks() {
        return List.copyOf(tasks);
    }

    public List<ApprovalTask> openTasks() {
        return tasks.stream().filter(task -> task.status().isOpen()).toList();
    }

    public ApprovalRequestId id() {
        return id;
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

    public EmployeeId initiator() {
        return initiator;
    }

    public int round() {
        return round;
    }

    public ApprovalInstanceId instanceId() {
        return instanceId;
    }

    public ApprovalStatus status() {
        return status;
    }

    public ApprovalSummary summary() {
        return summary;
    }

    public Optional<Reason> resultReason() {
        return Optional.ofNullable(resultReason);
    }

    public Optional<UUID> trigger() {
        return Optional.ofNullable(trigger);
    }

    public ApprovalStatus loadedStatus() {
        return loadedStatus;
    }

    public int loadedRound() {
        return loadedRound;
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean startsRound() {
        return isNew || round != loadedRound;
    }
}
