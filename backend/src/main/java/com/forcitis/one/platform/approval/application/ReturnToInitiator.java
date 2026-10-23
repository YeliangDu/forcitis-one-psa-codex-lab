package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A return ends the round; only that round's initiator (I-5) starts the next, and no history is deleted. */
@Service
public class ReturnToInitiator {

    private final TaskGuard guard;
    private final HookGate hooks;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final BusinessTime time;

    ReturnToInitiator(TaskGuard guard, HookGate hooks, ApprovalEngine engine, Approvals approvals, BusinessTime time) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.approvals = approvals;
        this.time = time;
    }

    @Transactional
    public DecisionResult execute(ApprovalTaskId taskId, UserId actor, String reason, OptionalLong expectedVersion) {
        TaskView task = guard.requireActor(taskId, actor, "return");
        Reason given = Reason.ofNullable(reason);
        if (given == null) {
            throw KernelError.KERNEL_REASON_REQUIRED.refusal("A return needs a reason");
        }
        ApprovalRequest request = approvals.lock(task.instanceId());
        approvals.requireVersion(request, expectedVersion);
        TaskView current = guard.requireActor(taskId, actor, "return");
        HookDecision hook = hooks.check(current, actor, HookAction.RETURN, reason);
        hook.variableUpdates().forEach(update -> engine.updateVariables(current.instanceId(),
                new ProcessVariables(Map.of(), Map.of(update.name(), update.values()))));
        if (!current.isClaimed()) {
            approvals.claim(request, current, actor);
        }
        request.completeTask(taskId, actor, TaskOutcome.RETURNED, false, given, current.contentHash(), time.moment());
        approvals.returned(request, given);
        approvals.save(request);
        return new DecisionResult(current.instanceId(), request.status());
    }
}
