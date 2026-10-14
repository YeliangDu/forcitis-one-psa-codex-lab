package com.forcitis.one.platform.approval.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Several row tasks of one (instance, node) are ONE to-do; the newest represents it. */
public final class InboxMerger {

    private InboxMerger() {
    }

    public static List<LegacyTodo> mergeRowTasks(List<LegacyTodo> todos) {
        Comparator<LegacyTodo> newestFirst = Comparator.comparing(
                (LegacyTodo t) -> t.createdAt() == null ? Instant.MIN : t.createdAt()).reversed();
        List<LegacyTodo> sorted = new ArrayList<>(todos);
        sorted.sort(newestFirst);
        Map<String, LegacyTodo> perNode = new LinkedHashMap<>();
        for (LegacyTodo todo : sorted) {
            perNode.putIfAbsent(todo.instanceId().value() + "\u0000" + todo.nodeKey(), todo);
        }
        return List.copyOf(perNode.values());
    }
}
