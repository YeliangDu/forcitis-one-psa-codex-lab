package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.application.TaskDetail;
import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/** reason is absent when the deciding person gave none: the screen words that, the data does not pretend (RULE-280). */
record TaskDetailResponse(String taskId, String instanceId, String processKey, String documentType, String documentId, String nodeKey,
                          @Nullable String nodeName, String initiator, String initiatorName, @Nullable String initiatorOrgUnitName,
                          @Nullable String assignee, @Nullable String assigneeName, List<String> candidates, List<String> candidateNames,
                          Instant createdAt, String status, @Nullable Instant dueAt, @Nullable Instant deadlineAt, boolean overdue,
                          @Nullable Instant completedAt, @Nullable String outcome, @Nullable String effect, @Nullable String reason,
                          @Nullable String summary, boolean canAct, List<HistoryEntry> history) {

    record HistoryEntry(String taskId, String nodeKey, int round, String status, @Nullable String assignee, @Nullable String outcome,
                        @Nullable String effect, @Nullable String reason, @Nullable Instant completedAt) {
    }

    static TaskDetailResponse of(TaskDetail detail) {
        return new TaskDetailResponse(detail.taskId().value(), detail.instanceId().value(), detail.processKey().name(),
                detail.document().documentType().name(), detail.document().documentId().value(), detail.nodeKey(),
                detail.nodeName().orElse(null), String.valueOf(detail.initiator().value()), detail.initiatorName(),
                detail.initiatorOrgUnitName().orElse(null), detail.assignee().map(a -> String.valueOf(a.value())).orElse(null),
                detail.assigneeName().orElse(null), detail.candidates().stream().map(UserId::value).map(String::valueOf).toList(),
                detail.candidateNames(), detail.createdAt(), detail.status().name(), detail.dueAt().orElse(null), detail.dueAt().orElse(null),
                detail.overdue(), detail.completedAt().orElse(null), detail.outcome().map(TaskOutcome::name).orElse(null),
                detail.effect().map(TaskEffect::name).orElse(null), detail.reason().map(Reason::value).orElse(null),
                detail.summary().map(ApprovalSummary::value).orElse(null), detail.canAct(),
                detail.history().stream().map(e -> new HistoryEntry(e.taskId().value(), e.nodeKey().value(), e.round(), e.status().name(),
                        e.assignee().map(a -> String.valueOf(a.value())).orElse(null), e.outcome().map(TaskOutcome::name).orElse(null),
                        e.effect().map(TaskEffect::name).orElse(null), e.reason().map(Reason::value).orElse(null),
                        e.completedAt().orElse(null))).toList());
    }
}
