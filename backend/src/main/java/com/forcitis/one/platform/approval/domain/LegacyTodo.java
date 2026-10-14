package com.forcitis.one.platform.approval.domain;

import java.time.Instant;
import java.util.Objects;

/** Row tasks appear several times (merged by InboxMerger). */
public record LegacyTodo(String taskKey, LegacyInstanceId instanceId, String nodeKey, String nodeName, String title,
                         Instant createdAt, String detailUrl) {

    public LegacyTodo {
        Objects.requireNonNull(taskKey, "taskKey");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(nodeKey, "nodeKey");
    }
}
