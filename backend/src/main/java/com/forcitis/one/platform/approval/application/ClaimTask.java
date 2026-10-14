package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A candidate claims a task (RULE-249/276). After the claim only the claimer may act (I-7). Claiming a task one already
 * holds is a harmless repeat; claiming one somebody else holds is refused as "not your task".
 */
@Service
public class ClaimTask {

    private final TaskGuard guard;
    private final ApprovalLifecycle lifecycle;
    private final AuditLog audit;

    ClaimTask(TaskGuard guard, ApprovalLifecycle lifecycle, AuditLog audit) {
        this.guard = guard;
        this.lifecycle = lifecycle;
        this.audit = audit;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor) {
        TaskView task = guard.requireActor(taskId, actor, "claim");
        if (task.isClaimed()) {
            return;
        }
        lifecycle.claim(task, actor);
        audit.record(new AuditEntry("platform.approval.task.claim", actor, null, "approval-task:" + taskId.value(), null));
    }
}
