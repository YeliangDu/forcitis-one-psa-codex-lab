package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.domain.NodeDeadline;
import java.util.List;
import java.util.Optional;

/** What the engine knows about a task it just created, plus the node's own parameters (BPMN extension attributes). */
public record TaskRegistration(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessName processName, NodeKey nodeKey,
                               NodeName nodeName, List<UserId> candidates, boolean autoApproveRepeat, Optional<NodeDeadline> deadline,
                               ContentHash contentHash) {

    public TaskRegistration {
        candidates = List.copyOf(candidates);
    }
}
