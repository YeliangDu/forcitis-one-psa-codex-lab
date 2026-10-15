package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.platform.integration.api.LegacyTodo;
import com.forcitis.one.platform.integration.domain.LegacyInstanceId;
import java.time.Instant;
import java.util.Objects;

/** One row of the to-do view; row tasks appear several times (merged by {@link InboxMerger}). */
record LegacyTodoRow(String taskKey, LegacyInstanceId instanceId, String nodeKey, String nodeName, String title, Instant createdAt,
                     String detailUrl) {

    LegacyTodoRow {
        Objects.requireNonNull(taskKey, "taskKey");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(nodeKey, "nodeKey");
    }

    LegacyTodo todo() {
        return new LegacyTodo(taskKey, nodeName, title, createdAt, detailUrl);
    }
}
