package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.TaskActors;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Audits a refusal in its own transaction so it survives the rollback, then throws AccessDeniedException (I-7). */
@Component
class TaskGuard {

    private final ApprovalEngine engine;
    private final DenialAudit audit;

    TaskGuard(ApprovalEngine engine, DenialAudit audit) {
        this.engine = engine;
        this.audit = audit;
    }

    TaskView requireTask(ApprovalTaskId taskId) {
        return engine.findTask(taskId).orElseThrow(() -> new ApprovalOperationException(
                ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND, "Task " + taskId.value() + " does not exist or is finished"));
    }

    TaskView requireActor(ApprovalTaskId taskId, UserId actor, String action) {
        TaskView task = requireTask(taskId);
        requireMayAct(task, actor, action);
        return task;
    }

    void requireMayAct(TaskView task, UserId actor, String action) {
        if (!TaskActors.mayAct(task, actor)) {
            deny(task, actor, action);
        }
    }

    /** Only the person who holds the task may hand it over; a mere candidate has to claim it first. */
    TaskView requireHandler(ApprovalTaskId taskId, UserId actor, String action) {
        TaskView task = requireTask(taskId);
        if (!task.assignee().map(actor::equals).orElse(false)) {
            deny(task, actor, action);
        }
        return task;
    }

    void deny(TaskView task, UserId actor, String action) {
        audit.record(new AuditEntry("platform.approval.task.denied", actor, null,
                "approval-task:" + task.taskId().value(), action));
        throw new AccessDeniedException("User " + actor.value() + " is not the handler of task " + task.taskId().value());
    }
}
