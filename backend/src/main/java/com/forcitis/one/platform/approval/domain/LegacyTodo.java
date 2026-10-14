package com.forcitis.one.platform.approval.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One in-progress task of the old system for one user (a row of the read-only inbox view). Row tasks appear several
 * times. detailUrl is the page in the OLD system the browser jumps to (built from the configured template).
 */
public record LegacyTodo(String taskKey, LegacyInstanceId instanceId, String nodeKey, String nodeName, String title,
                         Instant createdAt, String detailUrl) {

    public LegacyTodo {
        Objects.requireNonNull(taskKey, "taskKey");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(nodeKey, "nodeKey");
    }
}
