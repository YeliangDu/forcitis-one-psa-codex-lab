package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * nodeName and the candidates are known only while the task is open. outcome, effect and reason are the opinion the deciding person
 * gave; reason stays empty when none was given (RULE-280): the screen words that, the data does not pretend. version is what the
 * caller sends back as If-Match when it acts on the task.
 */
public record TaskDetail(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document,
                         String nodeKey, Optional<String> nodeName, UserId initiator, String initiatorName, Optional<String> initiatorOrgUnitName,
                         Optional<UserId> assignee, Optional<String> assigneeName, List<UserId> candidates, List<String> candidateNames,
                         Instant createdAt, ApprovalTaskStatus status, Optional<Instant> dueAt, boolean overdue, Optional<Instant> completedAt,
                         Optional<TaskOutcome> outcome, Optional<TaskEffect> effect, Optional<Reason> reason, Optional<ApprovalSummary> summary,
                         boolean canAct, long version, List<Entry> history) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
        candidateNames = List.copyOf(candidateNames);
        history = List.copyOf(history);
    }

    /** One task of the same document, in the order the tasks were created. */
    public record Entry(ApprovalTaskId taskId, NodeKey nodeKey, int round, ApprovalTaskStatus status, Optional<UserId> assignee,
                        Optional<TaskOutcome> outcome, Optional<TaskEffect> effect, Optional<Reason> reason, Optional<Instant> completedAt) {
    }
}
