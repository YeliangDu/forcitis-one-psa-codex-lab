package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The process administrator's read-only view of every open task, optionally only the overdue ones; looking is audited (D-1a-139). */
@Service
public class ListAllTasks {

    private final ProcessAdministrator administrator;
    private final ApprovalTaskQueries tasks;
    private final AuditLog audit;
    private final DenialAudit denials;
    private final BusinessTime time;

    ListAllTasks(ProcessAdministrator administrator, ApprovalTaskQueries tasks, AuditLog audit, DenialAudit denials, BusinessTime time) {
        this.administrator = administrator;
        this.tasks = tasks;
        this.audit = audit;
        this.denials = denials;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<TaskOverview> execute(UserId actor, boolean overdueOnly) {
        if (!administrator.isAdministrator(actor)) {
            denials.record(new AuditEntry("platform.approval.task.denied", actor, null, "approval-task:all", "list"));
            throw new AccessDeniedException("User " + actor.value() + " is not the process administrator");
        }
        audit.recordIndependently(new AuditEntry("platform.approval.task.list-all", actor, null, "approval-task:all",
                overdueOnly ? "overdue only" : null));
        Instant now = time.now();
        return tasks.openTasks(overdueOnly ? Optional.of(now) : Optional.empty()).stream()
                .map(record -> new TaskOverview(record.taskId(), record.processKey(), record.document(), record.nodeKey(), record.status(),
                        record.assignee(), record.createdAt(), record.dueAt(), record.dueAt().map(now::isAfter).orElse(false)))
                .toList();
    }
}
