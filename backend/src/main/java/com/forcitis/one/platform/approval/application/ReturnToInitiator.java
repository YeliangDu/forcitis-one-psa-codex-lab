package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
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
    private final AuditLog audit;

    ReturnToInitiator(TaskGuard guard, HookGate hooks, ApprovalEngine engine, ApprovalLifecycle lifecycle, AuditLog audit) {
        this.guard = guard;
        this.hooks = hooks;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Transactional
    public DecisionResult execute(ApprovalTaskId taskId, UserId actor, String reason) {
        TaskView task = guard.requireActor(taskId, actor, "return");
        if (reason == null || reason.isBlank()) {
            throw new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_REASON_REQUIRED, "A return needs a reason");
        }
        ApprovalRequestRecord request = lifecycle.lock(task.instanceId());
        TaskView current = guard.requireActor(taskId, actor, "return"); // under the lock: the task may have changed meanwhile
        HookDecision hook = hooks.check(current, actor, HookAction.RETURN, reason);
        hook.variableUpdates().forEach(update -> engine.updateVariables(current.instanceId(),
                new ProcessVariables(Map.of(), Map.of(update.name(), update.values()))));
        if (!current.isClaimed()) {
            lifecycle.claim(current, actor);
        }
        audit.record(new AuditEntry("platform.approval.task.return", actor, null, "approval-task:" + taskId.value(), reason));
        return new DecisionResult(current.instanceId().value(), lifecycle.returned(request, current, actor, reason).name());
    }
}
