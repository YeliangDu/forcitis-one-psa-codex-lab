package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.approval.domain.TaskView;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One task per transaction: a late task is reminded once its deadline has passed and, once the escalation point has passed too,
 * handed to the holders' superiors, or to the process administrator when a superior is unknown. It is never approved for anyone.
 */
@Component
class TaskDeadline {

    private static final Logger log = LoggerFactory.getLogger(TaskDeadline.class);

    private final ApprovalTaskRepository tasks;
    private final ApprovalLifecycle lifecycle;
    private final Aggregates aggregates;
    private final ApprovalEngine engine;
    private final OrgDirectory org;
    private final ProcessAdministrator administrator;
    private final MeterRegistry meters;
    private final BusinessTime time;

    TaskDeadline(ApprovalTaskRepository tasks, ApprovalLifecycle lifecycle, Aggregates aggregates, ApprovalEngine engine,
            OrgDirectory org, ProcessAdministrator administrator, MeterRegistry meters, BusinessTime time) {
        this.tasks = tasks;
        this.lifecycle = lifecycle;
        this.aggregates = aggregates;
        this.engine = engine;
        this.org = org;
        this.administrator = administrator;
        this.meters = meters;
        this.time = time;
    }

    @Transactional
    public void enforce(ApprovalTaskId taskId) {
        Optional<ApprovalInstanceId> instance = tasks.instanceOf(taskId);
        if (instance.isEmpty()) {
            return;
        }
        ApprovalRequest request = lifecycle.lock(instance.get());
        ApprovalTask task = aggregates.task(taskId);
        if (!task.status().isOpen() || !instance.get().equals(request.instanceId())) {
            return;
        }
        Optional<TaskView> view = engine.findTask(taskId);
        if (view.isEmpty()) {
            Transitions.run(task::close);
            aggregates.save(task);
            return;
        }
        Instant now = time.now();
        if (task.overdueAt().isEmpty() && task.dueAt().map(due -> !now.isBefore(due)).orElse(false)) {
            Transitions.run(() -> task.markOverdue(holders(view.get()), now, time.today()));
            meters.counter("platform.approval.task.overdue").increment();
        }
        if (task.overdueAt().isPresent() && task.escalatedAt().isEmpty() && task.escalateAt().map(at -> !now.isBefore(at)).orElse(false)) {
            escalate(task, view.get(), now);
        }
        aggregates.save(task);
    }

    private void escalate(ApprovalTask task, TaskView view, Instant now) {
        List<UserId> holders = holders(view);
        Set<UserId> superiors = new LinkedHashSet<>();
        for (UserId holder : holders) {
            org.findSupervisor(holder).filter(s -> !holders.contains(s) && !s.equals(view.initiator())).ifPresent(superiors::add);
        }
        boolean toAdministrator = superiors.isEmpty();
        if (toAdministrator) {
            UserId fallback = administrator.current().orElseThrow(() -> {
                meters.counter("platform.approval.task.escalation.unresolved").increment();
                log.error("Task {} is overdue, none of {} has a superior and no process administrator is configured", task.taskId().value(), holders);
                return new IllegalStateException("Nobody to escalate task " + task.taskId().value() + " to");
            });
            superiors.add(fallback);
            meters.counter("platform.approval.task.escalation.administrator").increment();
            log.error("Task {} is overdue and none of {} has a superior: handed to the process administrator", task.taskId().value(), holders);
        }
        List<UserId> receivers = new ArrayList<>(superiors);
        if (view.isClaimed()) {
            engine.releaseClaim(view.taskId());
        }
        engine.addCandidates(view.taskId(), receivers);
        Transitions.run(() -> task.escalate(receivers, toAdministrator, now, time.today()));
        meters.counter("platform.approval.task.escalated").increment();
    }

    private static List<UserId> holders(TaskView view) {
        return view.assignee().map(List::of).orElseGet(() -> view.candidates().stream()
                .sorted(java.util.Comparator.comparingLong(UserId::value)).toList());
    }
}
