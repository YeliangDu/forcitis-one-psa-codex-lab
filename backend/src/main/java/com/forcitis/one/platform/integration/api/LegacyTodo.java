package com.forcitis.one.platform.integration.api;

import java.time.Instant;
import java.util.Objects;

/** One to-do of the old system, the row tasks of one process instance and node already merged into it. */
public record LegacyTodo(String taskKey, String nodeName, String title, Instant createdAt, String detailUrl) {

    public LegacyTodo {
        Objects.requireNonNull(taskKey, "taskKey");
    }
}
