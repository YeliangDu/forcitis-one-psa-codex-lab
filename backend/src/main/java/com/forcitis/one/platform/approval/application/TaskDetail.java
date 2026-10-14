package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record TaskDetail(String taskId, String instanceId, String processKey, String businessKey, String nodeKey, String nodeName,
                         String initiator, @Nullable String assignee, List<String> candidates, Instant createdAt, boolean canAct) {

    public TaskDetail {
        candidates = List.copyOf(candidates);
    }
}
