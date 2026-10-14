package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What the engine knows when it creates a task, completed with the deadline the node's parameters give. */
public record TaskAssignment(ApprovalTaskId taskId, ApprovalInstanceId instanceId, NodeKey nodeKey, NodeName nodeName,
                             ProcessName processName, List<UserId> candidates, Optional<DeadlinePlan> deadline) {

    public TaskAssignment {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(processName, "processName");
        Objects.requireNonNull(deadline, "deadline");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("a task needs at least one candidate");
        }
    }
}
