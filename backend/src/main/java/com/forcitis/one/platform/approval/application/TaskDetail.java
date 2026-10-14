package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import java.util.List;

/**
 * One new task as its participants see it. canAct tells the caller whether the server would let them claim or decide it now
 * (the server checks again on every operation, I-7).
 */
public record TaskDetail(String taskId, String instanceId, String processKey, String businessKey, String nodeKey, String nodeName,
                         long initiator, Long assignee, List<Long> candidates, Instant createdAt, boolean initiatorNode,
                         boolean canAct) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
    }
}
