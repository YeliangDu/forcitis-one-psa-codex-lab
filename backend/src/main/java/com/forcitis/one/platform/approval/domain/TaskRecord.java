package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import java.time.Instant;
import java.util.Optional;

/** What the approval module recorded of a task: who decided, how and why. A read projection, never written back. */
public record TaskRecord(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document,
                         NodeKey nodeKey, UserId initiator, ApprovalTaskStatus status, Optional<UserId> assignee, Instant createdAt,
                         Optional<Instant> dueAt, Optional<Instant> completedAt, Optional<TaskOutcome> outcome,
                         Optional<TaskEffect> effect, Optional<Reason> reason, ApprovalSummary summary, long requestVersion,
                         int round) {
}
