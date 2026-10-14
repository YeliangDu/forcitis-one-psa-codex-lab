package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A return ends the round; only that round's initiator (I-5) starts the next, and no history is deleted. */
@Service
public class ReturnToInitiator {

    private final TaskGuard guard;
    private final HookGate hooks;
    private final ApprovalEngine engine;
    private final ApprovalLifecycle lifecycle;
    private final Aggregates aggregates;
    private final BusinessTime time;

    ReturnToInitiator(TaskGuard guard, HookGate hooks, ApprovalEngine engine, ApprovalLifecycle lifecycle, Aggregates aggregates,
            BusinessTime time) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.aggregates = aggregates;
        this.time = time;
    }

    @Transactional
    public DecisionResult execute(ApprovalTaskId taskId, UserId actor, String reason) {
        TaskView task = guard.requireActor(taskId, actor, "return");
        if (reason == null || reason.isBlank()) {
            throw ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED.refusal("A return needs a reason");
        }
        ApprovalRequest request = lifecycle.lock(task.instanceId());
        TaskView current = guard.requireActor(taskId, actor, "return"); // under the lock: the task may have changed meanwhile
        HookDecision hook = hooks.check(current, actor, HookAction.RETURN, reason);
        hook.variableUpdates().forEach(update -> engine.updateVariables(current.instanceId(),
                new ProcessVariables(Map.of(), Map.of(update.name(), update.values()))));
        ApprovalTask aggregate = aggregates.task(taskId);
        if (!current.isClaimed()) {
            lifecycle.claim(aggregate, current, actor);
        }
        Transitions.run(() -> aggregate.complete(actor, TaskOutcome.RETURNED, false, reason, current.contentDigest(), time.today()));
        aggregates.save(aggregate);
        return new DecisionResult(current.instanceId(), lifecycle.returned(request, reason));
    }
}
