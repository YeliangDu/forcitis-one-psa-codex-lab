package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.application.HandledTask;
import java.time.Instant;
import org.springframework.lang.Nullable;

/** reason is absent when the person gave none: the screen words that, the data does not pretend (RULE-280). */
record HandledTaskResponse(String taskId, String processKey, String documentType, String documentId, String nodeKey, String outcome,
                           String effect, @Nullable String reason, Instant completedAt) {

    static HandledTaskResponse of(HandledTask task) {
        return new HandledTaskResponse(task.taskId().value(), task.processKey().name(), task.document().documentType().name(),
                task.document().documentId().value(), task.nodeKey().value(), task.outcome().name(), task.effect().name(),
                task.reason().map(Reason::value).orElse(null), task.completedAt());
    }
}
