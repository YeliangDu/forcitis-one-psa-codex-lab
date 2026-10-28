package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.TaskHandling;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the person decided, newest first, with the opinion they gave, read from the append-only trail of task handling. */
@Service
public class ListHandledTasks {

    private final ApprovalEngine engine;
    private final ApprovalTaskQueries tasks;
    private final OrgDirectory org;
    private final AccountEmployees accountEmployees;

    ListHandledTasks(ApprovalEngine engine, ApprovalTaskQueries tasks, OrgDirectory org, AccountEmployees accountEmployees) {
        this.engine = engine;
        this.accountEmployees = accountEmployees;
        this.tasks = tasks;
        this.org = org;
    }

    @Transactional(readOnly = true)
    public HandledPage execute(UserId user, ListPage page) {
        List<TaskHandling> rows = tasks.handledBy(user, page.cursor().map(ListPage.Position::at),
                page.cursor().map(position -> Uuids.parseCanonical(position.id())), page.limit() + 1);
        boolean more = rows.size() > page.limit();
        List<TaskHandling> shown = more ? rows.subList(0, page.limit()) : rows;
        ProcessNames processNames = new ProcessNames(engine);
        List<HandledTask> items = shown.stream().map(row -> new HandledTask(row.recordId(), row.taskId(), row.instanceId(), row.processKey(),
                processNames.of(row.processKey()), row.document(), row.nodeKey(), row.nodeName(), row.action(), row.actor(), nameOf(row.actor()),
                row.onBehalfOf().map(this::nameOf), row.reason(), row.decidedAt(), row.round())).toList();
        Optional<String> next = more ? Optional.of(ListPage.cursorAfter(shown.getLast().decidedAt(), shown.getLast().recordId().value().toString()))
                : Optional.empty();
        return new HandledPage(items, next);
    }

    private String nameOf(UserId user) {
        return accountEmployees.employeeOf(user).flatMap(org::findEmployee).map(EmployeeProfile::name).orElse(user.value().toString());
    }
}
