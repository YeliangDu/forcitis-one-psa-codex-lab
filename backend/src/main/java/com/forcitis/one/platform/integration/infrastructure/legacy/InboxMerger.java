package com.forcitis.one.platform.integration.infrastructure.legacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Several row tasks of one (instance, node) are ONE to-do; the newest represents it. */
final class InboxMerger {

    private InboxMerger() {
    }

    static List<LegacyTodoRow> mergeRowTasks(List<LegacyTodoRow> todos) {
        Comparator<LegacyTodoRow> newestFirst = Comparator.comparing(
                (LegacyTodoRow t) -> t.createdAt() == null ? Instant.MIN : t.createdAt()).reversed();
        List<LegacyTodoRow> sorted = new ArrayList<>(todos);
        sorted.sort(newestFirst);
        Map<String, LegacyTodoRow> perNode = new LinkedHashMap<>();
        for (LegacyTodoRow todo : sorted) {
            perNode.putIfAbsent(todo.instanceId().value() + "\u0000" + todo.nodeKey(), todo);
        }
        return List.copyOf(perNode.values());
    }
}
