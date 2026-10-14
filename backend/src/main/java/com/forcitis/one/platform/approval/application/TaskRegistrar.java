package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Called by the engine when it creates a task, inside the caller's transaction; a calendar that cannot answer fails the whole command. */
@Component
public class TaskRegistrar {

    private final Aggregates aggregates;
    private final ApprovalTaskRepository tasks;
    private final WorkCalendar calendar;
    private final AuditLog audit;
    private final BusinessTime time;

    TaskRegistrar(Aggregates aggregates, ApprovalTaskRepository tasks, WorkCalendar calendar, AuditLog audit, BusinessTime time) {
        this.aggregates = aggregates;
        this.tasks = tasks;
        this.calendar = calendar;
        this.audit = audit;
        this.time = time;
    }

    /** True when the system passes the task itself: the only candidate already approved this node with exactly this content (RULE-302). */
    public boolean register(TaskRegistration registration) {
        if (isRepeatApprover(registration)) {
            aggregates.save(ApprovalTask.assignAutomatically(registration.taskId(), registration.instanceId(), registration.processKey(),
                    registration.document(), registration.legalEntityId(), registration.nodeKey()));
            audit.record(new AuditEntry("platform.approval.task.auto-approve", null, null,
                    "approval-task:" + registration.taskId().value(), "same approver, same node, same content"));
            return true;
        }
        Instant due = null;
        Instant escalate = null;
        if (registration.deadlineWorkdays().isPresent()) {
            due = calendar.endOfWorkdayAfter(registration.submittedOn(), registration.deadlineWorkdays().getAsInt(), registration.legalEntityId());
            LocalDate dueDate = LocalDate.ofInstant(due.minusSeconds(1), BusinessTime.ZONE);
            escalate = calendar.endOfWorkdayAfter(dueDate, registration.escalationWorkdays().getAsInt(), registration.legalEntityId());
        }
        aggregates.save(ApprovalTask.assign(registration.taskId(), registration.instanceId(), registration.processKey(),
                registration.processName(), registration.document(), registration.legalEntityId(), registration.nodeKey(),
                registration.nodeName(), registration.initiator(), registration.candidates(), due, escalate, time.today()));
        return false;
    }

    private boolean isRepeatApprover(TaskRegistration registration) {
        if (!registration.autoApproveRepeat() || registration.candidates().size() != 1) {
            return false;
        }
        UserId only = registration.candidates().getFirst();
        return tasks.hasApprovedSameContent(registration.instanceId(), registration.nodeKey(), only, registration.contentDigest());
    }
}
