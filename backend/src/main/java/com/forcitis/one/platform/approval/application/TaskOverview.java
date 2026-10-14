package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import java.time.Instant;
import java.util.Optional;

public record TaskOverview(ApprovalTaskId taskId, ProcessKey processKey, DocumentReference document, NodeKey nodeKey,
                           ApprovalTaskStatus status, Optional<UserId> assignee, Instant createdAt, Optional<Instant> dueAt,
                           boolean overdue) {
}
