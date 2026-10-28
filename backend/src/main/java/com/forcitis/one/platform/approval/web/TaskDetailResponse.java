package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.application.TaskDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * reason is absent when the deciding person gave none: the screen words that, the data does not pretend (RULE-280). processName is what a
 * person calls the process, from its definition; absent when the definition is no longer deployed.
 */
record TaskDetailResponse(UUID taskId, UUID instanceId, ProcessKey processKey, @Nullable String processName, DocumentTypeCode documentType, UUID documentId, String nodeKey,
                          @Nullable String nodeName, UUID initiator, String initiatorName, @Nullable String initiatorOrgUnitName,
                          @Nullable UUID assignee, @Nullable String assigneeName, List<UUID> candidates, List<String> candidateNames,
                          Instant createdAt, String status, @Nullable Instant dueAt, @Nullable Instant deadlineAt, boolean overdue,
                          @Nullable Instant completedAt, @Nullable String outcome, @Nullable String effect, @Nullable String reason,
                          @Nullable String summary, boolean canAct, List<HistoryEntry> history) {

    record HistoryEntry(UUID recordId, UUID taskId, String nodeKey, String nodeName, int round, TaskRecordAction action, UUID actorId,
                        String actorName, @Nullable String onBehalfOfName, @Nullable String assigneeName, @Nullable String reason,
                        Instant decidedAt) {
    }

    static TaskDetailResponse of(TaskDetail detail) {
        return new TaskDetailResponse(detail.taskId().value(), detail.instanceId().value(), detail.processKey(),
                detail.processName().map(ProcessName::value).orElse(null), detail.document().documentType(), detail.document().documentId().value(), detail.nodeKey(),
                detail.nodeName().orElse(null), detail.initiator().value(), detail.initiatorName(),
                detail.initiatorOrgUnitName().orElse(null), detail.assignee().map(UserId::value).orElse(null),
                detail.assigneeName().orElse(null), detail.candidates().stream().map(UserId::value).toList(),
                detail.candidateNames(), detail.createdAt(), detail.status().name(), detail.dueAt().orElse(null), detail.dueAt().orElse(null),
                detail.overdue(), detail.completedAt().orElse(null), detail.outcome().map(TaskOutcome::name).orElse(null),
                detail.effect().map(TaskEffect::name).orElse(null), detail.reason().map(Reason::value).orElse(null),
                detail.summary().map(ApprovalSummary::value).orElse(null), detail.canAct(),
                detail.history().stream().map(e -> new HistoryEntry(e.recordId().value(), e.taskId().value(), e.nodeKey().value(),
                        e.nodeName().value(), e.round(), e.action(), e.actor().value(), e.actorName(),
                        e.onBehalfOfName().orElse(null), e.assigneeName().orElse(null), e.reason().map(Reason::value).orElse(null),
                        e.decidedAt())).toList());
    }
}
