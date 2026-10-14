package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.Set;

/**
 * An open task as the engine port reports it. assignee is null until a candidate claims it; from then on only the
 * assignee may act (I-7, RULE-276). insideSubProcess decides what a reject means (RULE-271/290).
 */
public record TaskView(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey,
                       String nodeKey, String nodeName, UserId initiator, UserId assignee, Set<UserId> candidates,
                       Instant createdAt, boolean insideSubProcess, String beforeHookKey) {

    public TaskView {
        candidates = Set.copyOf(candidates);
    }

    public boolean isClaimed() {
        return assignee != null;
    }
}
