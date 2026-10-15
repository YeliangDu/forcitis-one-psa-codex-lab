package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.TaskOverview;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

record TaskOverviewResponse(String taskId, ProcessKey processKey, DocumentType documentType, String documentId, String nodeKey, String status,
                            @Nullable UUID assignee, Instant createdAt, @Nullable Instant dueAt, boolean overdue) {

    static TaskOverviewResponse of(TaskOverview task) {
        return new TaskOverviewResponse(task.taskId().value(), task.processKey(), task.document().documentType(),
                task.document().documentId().value(), task.nodeKey().value(), task.status().name(),
                task.assignee().map(UserId::value).orElse(null), task.createdAt(), task.dueAt().orElse(null),
                task.overdue());
    }
}
