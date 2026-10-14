package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record TaskDetail(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, DocumentReference document,
                         String nodeKey, String nodeName, UserId initiator, Optional<UserId> assignee, List<UserId> candidates,
                         Instant createdAt, boolean canAct) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
    }
}
