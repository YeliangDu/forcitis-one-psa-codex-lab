package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.OptionalLong;
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
    private final AccountEmployees accountEmployees;

    TransferTask(TaskGuard guard, ApprovalEngine engine, Approvals approvals, OrgDirectory org, BusinessTime time, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.guard = guard;
        this.engine = engine;
        this.approvals = approvals;
        this.org = org;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, EmployeeId to, String reason, OptionalLong expectedVersion) {
        TaskView first = guard.requireHandler(taskId, actor, "transfer");
        ApprovalRequest request = approvals.lock(first.instanceId());
        approvals.requireVersion(request, expectedVersion);
        TaskView task = guard.requireHandler(taskId, actor, "transfer");
        UserId assignee = accountEmployees.accountOf(to).orElseThrow(() -> ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                "Transfer target " + to.value() + " has no account"));
        requireValidTarget(task, actor, to, assignee);
        requireNamedByNode(task, to, assignee);
        requireNotDecidedOnNode(request, task, to, assignee);
        engine.reassignTask(taskId, assignee);
        request.transferTask(taskId, actor, assignee, Reason.ofNullable(reason), time.moment());
        approvals.save(request);
    }

    private void requireValidTarget(TaskView task, UserId actor, EmployeeId to, UserId assignee) {
        boolean employed = org.findEmployee(to).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
        if (!employed) {
            throw refused("Transfer target " + to.value() + " is not an employed person");
        }
        if (assignee.equals(actor) || to.equals(task.initiator())) {
            throw refused("A task cannot be handed to its handler or to the initiator");
        }
        boolean alreadyOnNode = engine.findOpenTasks(task.instanceId()).stream()
                .filter(other -> other.nodeKey().equals(task.nodeKey()) && !other.taskId().equals(task.taskId()))
                .anyMatch(other -> other.candidates().contains(assignee) || other.assignee().map(assignee::equals).orElse(false));
        if (alreadyOnNode) {
            throw refused("Transfer target " + to.value() + " already has a task on this node");
        }
    }

    /** A node that takes no fallback is handed over only inside the people its rule names. */
    private void requireNamedByNode(TaskView task, EmployeeId to, UserId assignee) {
        if (engine.handoverTargetsOf(task.taskId()).map(named -> !named.contains(assignee)).orElse(false)) {
            throw refused("Transfer target " + to.value() + " is not a person node " + task.nodeKey() + " names, and the node takes no fallback");
        }
    }

    /** A countersign task does not go to somebody who already decided a task of the node in this round. */
    private static void requireNotDecidedOnNode(ApprovalRequest request, TaskView task, EmployeeId to, UserId assignee) {
        if (request.hasDecidedOnNodeOf(task.taskId(), assignee)) {
            throw refused("Transfer target " + to.value() + " already decided a task of node " + task.nodeKey() + " in this round");
        }
    }

    private static DomainException refused(String message) {
        return ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(message);
    }
}
