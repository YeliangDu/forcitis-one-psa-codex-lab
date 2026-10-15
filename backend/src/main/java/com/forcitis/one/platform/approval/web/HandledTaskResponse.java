package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.platform.approval.application.HandledTask;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** reason is absent when the person gave none: the screen words that, the data does not pretend (RULE-280). */
record HandledTaskResponse(UUID recordId, String taskId, String instanceId, ProcessKey processKey, DocumentType documentType, String documentId,
                           String nodeKey, String nodeName, TaskRecordAction action, UUID actorId, String actorName,
                           @Nullable String onBehalfOfName, @Nullable String reason, Instant decidedAt, int round) {

    static HandledTaskResponse of(HandledTask task) {
        return new HandledTaskResponse(task.recordId().value(), task.taskId().value(), task.instanceId().value(), task.processKey(),
                task.document().documentType(), task.document().documentId().value(), task.nodeKey().value(), task.nodeName().value(),
                task.action(), task.actor().value(), task.actorName(), task.onBehalfOfName().orElse(null),
                task.reason().map(Reason::value).orElse(null), task.decidedAt(), task.round());
    }
}
