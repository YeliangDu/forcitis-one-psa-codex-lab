package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
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
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One task per transaction, under the lock of the request it belongs to: a late task is reminded once its reminder time has come
 * and, once the escalation time has come too, handed on. A claimed task goes to the claimant's superior (the claimant stays a
 * candidate); an unclaimed one goes to the process administrator; with no superior known it goes to the administrator and raises an
 * alert (D-1a-113). It is never approved for anyone (RULE-382).
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

    TaskDeadline(ApprovalRequestRepository requests, Approvals approvals, ApprovalEngine engine, OrgDirectory org,
            ProcessAdministrator administrator, MeterRegistry meters, BusinessTime time, AccountEmployees accountEmployees) {
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
        UserId superior = view.assignee().map(accountEmployees::employeeOf).flatMap(org::findSupervisor)
                .filter(supervisor -> !supervisor.equals(view.initiator())).map(accountEmployees::accountOf)
                .filter(supervisor -> !holders.contains(supervisor)).orElse(null);
        boolean toAdministrator = superior == null;
        List<UserId> receivers = toAdministrator ? administratorsFor(view, holders) : List.of(superior);
        if (view.isClaimed()) {
            engine.releaseClaim(view.taskId());
        }
        engine.addCandidates(view.taskId(), receivers);
        request.escalateTask(view.taskId(), receivers, toAdministrator, now);
        meters.counter("forcitis.platform.approval.task.escalated").increment();
    }

    private List<UserId> administratorsFor(TaskView view, List<UserId> holders) {
        List<UserId> fallback = administrator.holders().stream().sorted(Comparator.comparingLong(UserId::value)).toList();
        if (fallback.isEmpty()) {
            meters.counter("forcitis.platform.approval.task.escalation.unresolved").increment();
            log.error("Task {} is overdue and nobody can take it from {}: nobody holds the process administrator role", view.taskId().value(),
                    holders);
            throw ApprovalError.PLATFORM_APPROVAL_ESCALATION_TARGET_MISSING.refusal("Nobody to escalate task " + view.taskId().value() + " to");
        }
        meters.counter("forcitis.platform.approval.task.escalation.administrator").increment();
        if (view.isClaimed()) {
            log.error("Task {} is overdue and its holder {} has no superior: handed to the process administrator", view.taskId().value(),
                    holders);
        } else {
            log.warn("Task {} is overdue and unclaimed: handed to the process administrator", view.taskId().value());
        }
        return fallback;
    }

    private static List<UserId> holders(TaskView view) {
        return view.assignee().map(List::of).orElseGet(() -> view.candidates().stream().sorted(Comparator.comparingLong(UserId::value)).toList());
    }
}
