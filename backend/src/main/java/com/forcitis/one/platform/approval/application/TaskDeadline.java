package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.Moment;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.approval.domain.TaskView;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One task per transaction, under the lock of the request it belongs to: a late task is reminded once its reminder time has come
 * and, once the escalation time has come too, handed on. A claimed task goes to the claimant's superior (the claimant stays a
 * candidate); an unclaimed one goes to the process administrator; with no superior known it goes to the administrator and raises an
 * alert (D-1a-113). On a node with several tasks (a countersign) the line is walked up past a supervisor who holds or decided another task of
 * the node, and the administrator is chosen the same way (#1546); when somebody was passed over and nobody is left, the task stays overdue with its
 * holder and raises the unresolved alert instead of going to the administrator. It is never approved for anyone (RULE-382).
 */
@Component
class TaskDeadline {

    private static final Logger log = LoggerFactory.getLogger(TaskDeadline.class);

    private final ApprovalRequestRepository requests;
    private final Approvals approvals;
    private final ApprovalEngine engine;
    private final OrgDirectory org;
    private final ProcessAdministrator administrator;
    private final MeterRegistry meters;
    private final BusinessTime time;
    private final AccountEmployees accountEmployees;
    private final NodeShares shares;

    TaskDeadline(ApprovalRequestRepository requests, Approvals approvals, ApprovalEngine engine, OrgDirectory org,
            ProcessAdministrator administrator, MeterRegistry meters, BusinessTime time, AccountEmployees accountEmployees, NodeShares shares) {
        this.shares = shares;
        this.accountEmployees = accountEmployees;
        this.requests = requests;
        this.approvals = approvals;
        this.engine = engine;
        this.org = org;
        this.administrator = administrator;
        this.meters = meters;
        this.time = time;
    }

    @Transactional
    public void enforce(ApprovalTaskId taskId) {
        Optional<ApprovalRequest> found = requests.findByTaskForUpdate(taskId);
        if (found.isEmpty()) {
            return;
        }
        ApprovalRequest request = found.get();
        ApprovalTask task = request.task(taskId);
        if (!task.status().isOpen()) {
            return;
        }
        Moment now = time.moment();
        Optional<TaskView> view = task.instanceId().equals(request.instanceId()) ? engine.findTask(taskId) : Optional.empty();
        if (view.isEmpty()) {
            request.closeTask(taskId, now);
            approvals.save(request);
            return;
        }
        if (task.overdueAt().isEmpty() && task.deadline().map(plan -> !now.at().isBefore(plan.remindAt())).orElse(false)) {
            request.markTaskOverdue(taskId, holders(view.get()), now);
            meters.counter("forcitis.platform.approval.task.overdue").increment();
        }
        if (task.overdueAt().isPresent() && task.escalatedAt().isEmpty()
                && task.deadline().map(plan -> !now.at().isBefore(plan.escalateAt())).orElse(false)) {
            escalate(request, view.get(), now);
        }
        approvals.save(request);
    }

    private void escalate(ApprovalRequest request, TaskView view, Moment now) {
        List<UserId> holders = holders(view);
        Superior superior = superiorOf(request, view, holders);
        List<UserId> receivers;
        boolean toAdministrator = false;
        if (superior.found().isPresent()) {
            receivers = List.of(superior.found().get());
        } else if (superior.skippedSomebody()) {
            receivers = List.of();
        } else {
            toAdministrator = true;
            receivers = administratorsFor(view, holders).stream().filter(each -> !shares.takenElsewhere(request, view, each)).toList();
        }
        if (receivers.isEmpty()) {
            meters.counter("forcitis.platform.approval.task.escalation.unresolved").increment();
            log.error("Task {} is overdue and stays with {}: everybody it could go to already has another task of node {}", view.taskId().value(),
                    holders, view.nodeKey());
            return;
        }
        if (toAdministrator) {
            meters.counter("forcitis.platform.approval.task.escalation.administrator").increment();
        }
        if (view.isClaimed()) {
            engine.releaseClaim(view.taskId());
        }
        engine.addCandidates(view.taskId(), receivers);
        request.escalateTask(view.taskId(), receivers, toAdministrator, now);
        meters.counter("forcitis.platform.approval.task.escalated").increment();
    }

    /**
     * The first supervisor up the line who is not a holder or somebody with another task of the node; they are walked past (and counted as skipped).
     * The initiator, a supervisor without an account, or the top of the line ends the walk.
     */
    private Superior superiorOf(ApprovalRequest request, TaskView view, List<UserId> holders) {
        Optional<EmployeeId> current = view.assignee().flatMap(accountEmployees::employeeOf);
        Set<EmployeeId> seen = new HashSet<>();
        boolean skipped = false;
        while (current.isPresent() && seen.add(current.get())) {
            Optional<EmployeeId> next = org.findSupervisor(current.get());
            if (next.isEmpty() || next.get().equals(view.initiator())) {
                break;
            }
            Optional<UserId> account = accountEmployees.accountOf(next.get());
            if (account.isEmpty()) {
                break;
            }
            if (!holders.contains(account.get()) && !shares.takenElsewhere(request, view, account.get())) {
                return new Superior(account, skipped);
            }
            skipped = true;
            current = next;
        }
        return new Superior(Optional.empty(), skipped);
    }

    private record Superior(Optional<UserId> found, boolean skippedSomebody) {
    }

    private List<UserId> administratorsFor(TaskView view, List<UserId> holders) {
        List<UserId> fallback = administrator.holders().stream().sorted(Comparator.comparing(id -> id.value().toString())).toList();
        if (fallback.isEmpty()) {
            meters.counter("forcitis.platform.approval.task.escalation.unresolved").increment();
            log.error("Task {} is overdue and nobody can take it from {}: nobody holds the process administrator role", view.taskId().value(),
                    holders);
            throw ApprovalError.PLATFORM_APPROVAL_ESCALATION_TARGET_MISSING.refusal("Nobody to escalate task " + view.taskId().value() + " to");
        }
        if (view.isClaimed()) {
            log.error("Task {} is overdue and its holder {} has no superior: handed to the process administrator", view.taskId().value(),
                    holders);
        } else {
            log.warn("Task {} is overdue and unclaimed: handed to the process administrator", view.taskId().value());
        }
        return fallback;
    }

    private static List<UserId> holders(TaskView view) {
        return view.assignee().map(List::of).orElseGet(() -> view.candidates().stream().sorted(Comparator.comparing(id -> id.value().toString())).toList());
    }
}
