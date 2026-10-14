package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the person holding the task hands it over (RULE-285); the new handler must be employed, not the initiator and not already on the node. */
@Service
public class TransferTask {

    private final TaskGuard guard;
    private final ApprovalEngine engine;
    private final ApprovalLifecycle lifecycle;
    private final Aggregates aggregates;
    private final OrgDirectory org;
    private final AuditLog audit;
    private final BusinessTime time;

    TransferTask(TaskGuard guard, ApprovalEngine engine, ApprovalLifecycle lifecycle, Aggregates aggregates, OrgDirectory org,
            AuditLog audit, BusinessTime time) {
        this.guard = guard;
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.aggregates = aggregates;
        this.org = org;
        this.audit = audit;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, UserId to) {
        TaskView first = guard.requireHandler(taskId, actor, "transfer");
        lifecycle.lock(first.instanceId());
        TaskView task = guard.requireHandler(taskId, actor, "transfer");
        requireValidTarget(task, actor, to);
        ApprovalTask aggregate = aggregates.task(taskId);
        engine.reassignTask(taskId, to);
        Transitions.run(() -> aggregate.transfer(actor, to, time.today()));
        aggregates.save(aggregate);
        audit.record(new AuditEntry("platform.approval.task.transfer", actor, null, "approval-task:" + taskId.value(),
                "to:" + to.value()));
    }

    private void requireValidTarget(TaskView task, UserId actor, UserId to) {
        boolean employed = org.findEmployee(to).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
        if (!employed) {
            throw refused("Transfer target " + to.value() + " is not an employed person");
        }
        if (to.equals(actor) || to.equals(task.initiator())) {
            throw refused("A task cannot be handed to its handler or to the initiator");
        }
        boolean alreadyOnNode = engine.findOpenTasks(task.instanceId()).stream()
                .filter(other -> other.nodeKey().equals(task.nodeKey()) && !other.taskId().equals(task.taskId()))
                .anyMatch(other -> other.candidates().contains(to) || other.assignee().map(to::equals).orElse(false));
        if (alreadyOnNode) {
            throw refused("Transfer target " + to.value() + " already has a task on this node");
        }
    }

    private static ApprovalOperationException refused(String message) {
        return new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID, message);
    }
}
