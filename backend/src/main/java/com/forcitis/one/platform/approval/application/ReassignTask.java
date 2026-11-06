package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.approval.domain.TaskView;
import com.forcitis.one.platform.audit.api.AuditEntry;
import org.springframework.security.access.AccessDeniedException;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The process administrator hands any open task to an employed person other than the initiator, saying why; the event is audited (D-1a-61). */
@Service
public class ReassignTask {

    private final ProcessAdministrator administrator;
    private final TaskGuard guard;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final OrgDirectory org;
    private final DenialAudit denials;
    private final BusinessTime time;
    private final AccountEmployees accountEmployees;

    ReassignTask(ProcessAdministrator administrator, TaskGuard guard, ApprovalEngine engine, Approvals approvals, OrgDirectory org,
            DenialAudit denials, BusinessTime time, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.administrator = administrator;
        this.guard = guard;
        this.engine = engine;
        this.approvals = approvals;
        this.org = org;
        this.denials = denials;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, EmployeeId to, String reason, OptionalLong expectedVersion) {
        if (!administrator.isAdministrator(actor)) {
            denials.record(new AuditEntry("platform.approval.task.denied", actor, null, AuditSubject.of(AuditSubjectKind.APPROVAL_TASK, taskId.value()), "reassign"));
            throw new AccessDeniedException("User " + actor.value() + " is not the process administrator");
        }
        TaskView first = guard.requireTask(taskId);
        ApprovalRequest request = approvals.lock(first.instanceId());
        approvals.requireVersion(request, expectedVersion);
        TaskView task = guard.requireTask(taskId);
        boolean employed = org.findEmployee(to).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
        if (!employed || to.equals(task.initiator())) {
            throw ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                    "Reassignment target " + to.value() + " is not an employed person other than the initiator");
        }
        UserId assignee = accountEmployees.accountOf(to).orElseThrow(() -> ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                "Reassignment target " + to.value() + " has no account"));
        boolean alreadyOnTheNode = engine.findOpenTasksFor(assignee).stream().anyMatch(other -> other.instanceId().equals(task.instanceId())
                && other.nodeKey().equals(task.nodeKey()) && !other.taskId().equals(taskId));
        if (alreadyOnTheNode) {
            throw ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                    "Reassignment target " + to.value() + " already has a task on node " + task.nodeKey() + " of this approval");
        }
        if (engine.handoverTargetsOf(taskId).map(named -> !named.contains(assignee)).orElse(false)) {
            throw ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                    "Reassignment target " + to.value() + " is not a person node " + task.nodeKey() + " names, and the node takes no fallback");
        }
        if (request.hasDecidedOnNodeOf(taskId, assignee)) {
            throw ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID.refusal(
                    "Reassignment target " + to.value() + " already decided a task of node " + task.nodeKey() + " in this round");
        }
        engine.reassignTask(taskId, assignee);
        request.reassignTask(taskId, actor, assignee, Reason.ofNullable(reason), time.moment());
        approvals.save(request);
    }
}
