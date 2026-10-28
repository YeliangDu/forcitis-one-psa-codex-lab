package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.TaskActors;
import com.forcitis.one.platform.approval.domain.TaskRecord;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anybody but the initiator, the assignee and the candidates is told the task does not exist (fail closed). */
@Service
public class GetTaskDetail {

    private final ApprovalEngine engine;
    private final ApprovalTaskQueries tasks;
    private final OrgDirectory org;
    private final AccountEmployees accountEmployees;
    private final BusinessTime time;

    GetTaskDetail(ApprovalEngine engine, ApprovalTaskQueries tasks, OrgDirectory org, BusinessTime time, AccountEmployees accountEmployees) {
        this.accountEmployees = accountEmployees;
        this.engine = engine;
        this.tasks = tasks;
        this.org = org;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public TaskDetail execute(ApprovalTaskId taskId, UserId viewer) {
        Optional<TaskView> open = engine.findTask(taskId).filter(task -> isParticipant(task, viewer));
        Optional<TaskRecord> record = tasks.find(taskId);
        Instant now = time.now();
        if (open.isPresent()) {
            TaskView task = open.get();
            TaskRecord row = record.orElseThrow();
            List<UserId> candidates = task.candidates().stream().sorted(Comparator.comparing(this::nameOf).thenComparing(id -> id.value().toString())).toList();
            return detail(row, Optional.of(task.nodeName()), task.assignee(), candidates, TaskActors.mayAct(task, viewer), now);
        }
        TaskRecord decided = record.filter(r -> !r.status().isOpen())
                .filter(r -> isInitiator(r.initiator(), viewer) || r.assignee().map(viewer::equals).orElse(false))
                .orElseThrow(() -> ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND.refusal("Task " + taskId.value() + " does not exist"));
        return detail(decided, Optional.empty(), decided.assignee(), List.of(), false, now);
    }

    private TaskDetail detail(TaskRecord row, Optional<String> nodeName, Optional<UserId> assignee, List<UserId> candidates, boolean canAct,
            Instant now) {
        Optional<EmployeeProfile> initiator = org.findEmployee(row.initiator());
        List<TaskDetail.Entry> history = tasks.historyOf(row.processKey(), row.document().documentId()).stream()
                .map(r -> new TaskDetail.Entry(r.recordId(), r.taskId(), r.nodeKey(), r.nodeName(), r.round(), r.action(), r.actor(),
                        nameOf(r.actor()), r.onBehalfOf().map(this::nameOf), r.assignee().map(this::nameOf), r.reason(), r.decidedAt())).toList();
        return new TaskDetail(row.taskId(), row.instanceId(), row.processKey(), new ProcessNames(engine).of(row.processKey()), row.document(),
                row.nodeKey().value(), nodeName, row.initiator(),
                initiator.map(EmployeeProfile::name).orElse(String.valueOf(row.initiator().value())),
                initiator.flatMap(profile -> org.findOrgUnit(profile.orgUnitId())).map(OrgUnitProfile::name), assignee,
                assignee.map(this::nameOf), candidates, candidates.stream().map(this::nameOf).toList(), row.createdAt(), row.status(),
                row.dueAt(), row.status().isOpen() && row.dueAt().map(now::isAfter).orElse(false), row.completedAt(), row.outcome(), row.effect(),
                row.reason(), Optional.of(row.summary()), canAct, row.requestVersion(), history);
    }

    private String nameOf(UserId user) {
        return accountEmployees.employeeOf(user).flatMap(org::findEmployee).map(EmployeeProfile::name).orElse(user.value().toString());
    }

    private boolean isInitiator(EmployeeId initiator, UserId viewer) {
        return accountEmployees.employeeOf(viewer).map(initiator::equals).orElse(false);
    }

    private boolean isParticipant(TaskView task, UserId viewer) {
        return isInitiator(task.initiator(), viewer) || task.assignee().map(viewer::equals).orElse(false) || task.candidates().contains(viewer);
    }
}
