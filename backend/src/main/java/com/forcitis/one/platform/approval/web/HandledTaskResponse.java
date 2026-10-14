package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.application.HandledTask;
import java.time.Instant;
import org.springframework.lang.Nullable;

/** reason is absent when the person gave none: the screen words that, the data does not pretend (RULE-280). */
record HandledTaskResponse(String recordId, String taskId, String instanceId, String processKey, String documentType, String documentId,
                           String nodeKey, String nodeName, String action, String actorId, String actorName,
                           @Nullable String onBehalfOfName, @Nullable String reason, Instant decidedAt, int round) {

    static HandledTaskResponse of(HandledTask task) {
        return new HandledTaskResponse(task.recordId().value().toString(), task.taskId().value(), task.instanceId().value(), task.processKey().name(),
                task.document().documentType().name(), task.document().documentId().value(), task.nodeKey().value(), task.nodeName().value(),
                task.action().name(), String.valueOf(task.actor().value()), task.actorName(), task.onBehalfOfName().orElse(null),
                task.reason().map(Reason::value).orElse(null), task.decidedAt(), task.round());
    }
}
