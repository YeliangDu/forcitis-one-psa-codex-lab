package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;

/** Drives the WeCom card: one card per candidate (Q-3). */
public record TaskAssigned(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey,
                           String nodeKey, String nodeName, UserId initiator, List<UserId> candidates) {

    public TaskAssigned {
        candidates = List.copyOf(candidates);
    }
}
