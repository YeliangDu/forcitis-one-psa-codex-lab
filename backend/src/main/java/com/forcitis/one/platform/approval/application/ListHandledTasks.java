package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.TaskHandling;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the person decided, newest first, with the opinion they gave, read from the append-only trail of task handling. */
@Service
public class ListHandledTasks {

    private final ApprovalTaskQueries tasks;
    private final OrgDirectory org;

    ListHandledTasks(ApprovalTaskQueries tasks, OrgDirectory org) {
        this.tasks = tasks;
        this.org = org;
    }

    @Transactional(readOnly = true)
    public HandledPage execute(UserId user, ListPage page) {
        List<TaskHandling> rows = tasks.handledBy(user, page.cursor().map(ListPage.Position::at),
                page.cursor().map(position -> UUID.fromString(position.id())), page.limit() + 1);
        boolean more = rows.size() > page.limit();
        List<TaskHandling> shown = more ? rows.subList(0, page.limit()) : rows;
        List<HandledTask> items = shown.stream().map(row -> new HandledTask(row.recordId(), row.taskId(), row.instanceId(), row.processKey(),
                row.document(), row.nodeKey(), row.nodeName(), row.action(), row.actor(), nameOf(row.actor()),
                row.onBehalfOf().map(this::nameOf), row.reason(), row.decidedAt(), row.round())).toList();
        Optional<String> next = more ? Optional.of(ListPage.cursorAfter(shown.getLast().decidedAt(), shown.getLast().recordId().value().toString()))
                : Optional.empty();
        return new HandledPage(items, next);
    }

    private String nameOf(UserId user) {
        return org.findEmployee(user).map(EmployeeProfile::name).orElse(String.valueOf(user.value()));
    }
}
