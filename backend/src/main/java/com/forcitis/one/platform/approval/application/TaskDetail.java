package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import java.util.List;

/** Person ids are strings: signed 64-bit numbers that JavaScript cannot hold exactly. */
public record TaskDetail(String taskId, String instanceId, String processKey, String businessKey, String nodeKey, String nodeName,
                         String initiator, String assignee, List<String> candidates, Instant createdAt, boolean canAct) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
    }
}
