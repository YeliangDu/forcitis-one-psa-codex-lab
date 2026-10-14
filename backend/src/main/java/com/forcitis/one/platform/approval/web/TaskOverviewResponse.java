package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.application.TaskOverview;
import java.time.Instant;
import org.springframework.lang.Nullable;

record TaskOverviewResponse(String taskId, String processKey, String documentType, String documentId, String nodeKey, String status,
                            @Nullable String assignee, Instant createdAt, @Nullable Instant dueAt, boolean overdue) {

    static TaskOverviewResponse of(TaskOverview task) {
        return new TaskOverviewResponse(task.taskId().value(), task.processKey().name(), task.document().documentType().name(),
                task.document().documentId().value(), task.nodeKey().value(), task.status().name(),
                task.assignee().map(UserId::value).map(String::valueOf).orElse(null), task.createdAt(), task.dueAt().orElse(null),
                task.overdue());
    }
}
