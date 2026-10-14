package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskResulted;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * State transitions of one approval request and the typed events that announce them (RULE-289/306). Every transition runs
 * inside the caller's transaction: the engine change, the status row and the event registry entry commit together, which
 * is what makes delivery to business modules at-least-once (RULE-295/303).
 */
@Component
class ApprovalLifecycle {

    private final ApprovalEngine engine;
    private final ApprovalRequestRepository requests;
    private final ApplicationEventPublisher events;

    ApprovalLifecycle(ApprovalEngine engine, ApprovalRequestRepository requests, ApplicationEventPublisher events) {
        this.engine = engine;
        this.requests = requests;
        this.events = events;
    }

    /** Locks the request row of the instance for the rest of the transaction (serialises concurrent decisions). */
    ApprovalRequestRecord lock(ApprovalInstanceId instanceId) {
        return requests.lockByInstance(instanceId).orElseThrow(() -> new ApprovalOperationException(
                ApprovalError.PLATFORM_APPROVAL_REQUEST_NOT_FOUND, "No approval request for instance " + instanceId.value()));
    }

    /** Claims for the actor; a lost race against another candidate is a refusal (409), not a server error. */
    void claim(TaskView task, UserId actor) {
        if (!engine.claimTask(task.taskId(), actor)) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED,
                    "Task " + task.taskId().value() + " was claimed by somebody else");
        }
        events.publishEvent(new TaskClaimed(task.taskId(), actor));
    }

    void publishTaskResult(TaskView task, UserId actor, TaskOutcome outcome, String reason) {
        events.publishEvent(new TaskResulted(task.taskId(), task.instanceId(), task.processKey(), task.businessKey(),
                task.nodeKey(), actor, outcome, false, reason));
    }

    /** After a task completed (or the instance started): if the engine has no live instance left, the approval is APPROVED. */
    ApprovalStatus finishIfDone(ApprovalRequestRecord request, ApprovalStatus current) {
        if (engine.isRunning(request.instanceId())) {
            return current;
        }
        complete(request, ApprovalOutcome.APPROVED, ApprovalStatus.APPROVED, null);
        return ApprovalStatus.APPROVED;
    }

    /** A return ends the round (RULE-271/275): the instance ends as "returned", the initiator starts a new round; history stays. */
    ApprovalStatus returned(ApprovalRequestRecord request, TaskView task, UserId actor, String reason) {
        engine.endAsReturned(task.instanceId());
        publishTaskResult(task, actor, TaskOutcome.RETURNED, reason);
        complete(request, ApprovalOutcome.RETURNED, ApprovalStatus.RETURNED, reason);
        return ApprovalStatus.RETURNED;
    }

    /** A top-level reject ends the whole approval (RULE-271/290); history stays (RULE-288). */
    ApprovalStatus reject(ApprovalRequestRecord request, TaskView task, UserId actor, String reason) {
        engine.terminate(task.instanceId(), "REJECTED");
        publishTaskResult(task, actor, TaskOutcome.REJECTED, reason);
        complete(request, ApprovalOutcome.REJECTED, ApprovalStatus.REJECTED, reason);
        return ApprovalStatus.REJECTED;
    }

    private void complete(ApprovalRequestRecord request, ApprovalOutcome outcome, ApprovalStatus status, String reason) {
        requests.updateStatus(request.instanceId(), status, reason);
        events.publishEvent(new ApprovalCompleted(request.instanceId(), request.processKey(), request.businessKey(),
                request.round(), outcome, reason));
    }
}
