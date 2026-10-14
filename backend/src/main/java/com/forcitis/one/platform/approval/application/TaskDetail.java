package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import java.util.List;

/**
 * One new task as its participants see it. Person ids are strings: they are signed 64-bit numbers that JavaScript cannot hold exactly. canAct tells the caller whether the server would let them claim or decide it now
 * (the server checks again on every operation, I-7).
 */
public record TaskDetail(String taskId, String instanceId, String processKey, String businessKey, String nodeKey, String nodeName,
                         String initiator, String assignee, List<String> candidates, Instant createdAt, boolean canAct) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
    }
}
