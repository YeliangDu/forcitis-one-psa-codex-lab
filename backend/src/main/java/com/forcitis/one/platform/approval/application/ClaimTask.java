package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claiming a task one already holds is a harmless repeat; one somebody else holds is refused. */
@Service
public class ClaimTask {

    private final TaskGuard guard;
    private final ApprovalLifecycle lifecycle;
    private final Aggregates aggregates;
    private final AuditLog audit;

    ClaimTask(TaskGuard guard, ApprovalLifecycle lifecycle, Aggregates aggregates, AuditLog audit) {
        this.guard = guard;
        this.lifecycle = lifecycle;
        this.aggregates = aggregates;
        this.audit = audit;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor) {
        TaskView view = guard.requireActor(taskId, actor, "claim");
        if (view.isClaimed()) {
            return;
        }
        lifecycle.lock(view.instanceId());
        TaskView current = guard.requireActor(taskId, actor, "claim");
        if (current.isClaimed()) {
            return;
        }
        ApprovalTask task = aggregates.task(taskId);
        lifecycle.claim(task, current, actor);
        aggregates.save(task);
        audit.record(new AuditEntry("platform.approval.task.claim", actor, null, "approval-task:" + taskId.value(), null));
    }
}
