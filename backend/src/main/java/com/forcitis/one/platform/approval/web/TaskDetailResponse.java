package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.application.TaskDetail;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

record TaskDetailResponse(String taskId, String instanceId, String processKey, String documentType, String documentId, String nodeKey,
                          String nodeName, String initiator, @Nullable String assignee, List<String> candidates, Instant createdAt,
                          boolean canAct) {

    static TaskDetailResponse of(TaskDetail detail) {
        return new TaskDetailResponse(detail.taskId().value(), detail.instanceId().value(), detail.processKey().name(),
                detail.document().documentType().name(), detail.document().documentId().value(), detail.nodeKey(), detail.nodeName(),
                String.valueOf(detail.initiator().value()), detail.assignee().map(a -> String.valueOf(a.value())).orElse(null),
                detail.candidates().stream().map(UserId::value).map(String::valueOf).toList(), detail.createdAt(), detail.canAct());
    }
}
