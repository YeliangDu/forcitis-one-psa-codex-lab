package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.approval.domain.TaskView;
import org.springframework.stereotype.Component;

/**
 * Every transition runs in the caller's transaction, so the engine change, the aggregates and their events commit together. The
 * request is the aggregate root: its row is locked first, and its tasks change under that lock.
 */
@Component
class ApprovalLifecycle {

    private final ApprovalEngine engine;
    private final Aggregates aggregates;
    private final ApprovalTaskRepository tasks;
    private final BusinessTime time;

    ApprovalLifecycle(ApprovalEngine engine, Aggregates aggregates, ApprovalTaskRepository tasks, BusinessTime time) {
        this.engine = engine;
        this.aggregates = aggregates;
        this.tasks = tasks;
        this.time = time;
    }

    /** Serialises concurrent decisions for the rest of the transaction. */
    ApprovalRequest lock(ApprovalInstanceId instanceId) {
        return aggregates.requestOf(instanceId);
    }

    /** A lost race against another candidate is a refusal (409), not a server error. */
    void claim(ApprovalTask task, TaskView view, UserId actor) {
        if (!engine.claimTask(view.taskId(), actor)) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED,
                    "Task " + view.taskId().value() + " was claimed by somebody else");
        }
        Transitions.run(() -> task.claim(actor, time.today()));
    }

    /** APPROVED when the engine has no live instance left. */
    ApprovalStatus finishIfDone(ApprovalRequest request) {
        if (engine.isRunning(request.instanceId())) {
            return ApprovalStatus.RUNNING;
        }
        Transitions.run(() -> request.approve(time.today()));
        aggregates.save(request);
        return ApprovalStatus.APPROVED;
    }

    /** A return ends the round, the initiator starts a new one; history stays. */
    ApprovalStatus returned(ApprovalRequest request, String reason) {
        engine.endAsReturned(request.instanceId());
        Transitions.run(() -> request.returnRound(reason, time.today()));
        aggregates.save(request);
        closeOpenTasks(request.instanceId());
        return ApprovalStatus.RETURNED;
    }

    ApprovalStatus reject(ApprovalRequest request, String reason) {
        engine.terminate(request.instanceId(), "REJECTED");
        Transitions.run(() -> request.reject(reason, time.today()));
        aggregates.save(request);
        closeOpenTasks(request.instanceId());
        return ApprovalStatus.REJECTED;
    }

    void closeOpenTasks(ApprovalInstanceId instanceId) {
        for (ApprovalTask open : tasks.findOpenByInstance(instanceId)) {
            Transitions.run(open::close);
            aggregates.save(open);
        }
    }
}
