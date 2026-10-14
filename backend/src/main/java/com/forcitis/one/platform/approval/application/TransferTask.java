package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the person holding the task hands it over, saying why (RULE-285, D-1a-75); the new handler must be employed, not the initiator and not already on the node. */
@Service
public class TransferTask {

    private final TaskGuard guard;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final OrgDirectory org;
    private final BusinessTime time;

    TransferTask(TaskGuard guard, ApprovalEngine engine, Approvals approvals, OrgDirectory org, BusinessTime time) {
        this.guard = guard;
        this.engine = engine;
        this.approvals = approvals;
        this.org = org;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, UserId to, String reason) {
        TaskView first = guard.requireHandler(taskId, actor, "transfer");
        ApprovalRequest request = approvals.lock(first.instanceId());
        TaskView task = guard.requireHandler(taskId, actor, "transfer");
        requireValidTarget(task, actor, to);
        engine.reassignTask(taskId, to);
        request.transferTask(taskId, actor, to, Reason.ofNullable(reason), time.moment());
        approvals.save(request);
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

    private static DomainException refused(String message) {
        return ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(message);
    }
}
