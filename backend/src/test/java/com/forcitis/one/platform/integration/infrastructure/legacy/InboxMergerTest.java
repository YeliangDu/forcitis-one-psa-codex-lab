package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.domain.LegacyInstanceId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InboxMergerTest {

    static LegacyTodoRow row(String task, String instance, String node, String createdAt) {
        return new LegacyTodoRow(task, new LegacyInstanceId(instance), node, "Node " + node, "Title " + instance,
                createdAt == null ? null : Instant.parse(createdAt), null);
    }

    @Test
    @DisplayName("C1a-14 the row tasks of one instance and one node are one to-do, represented by the newest row")
    void rowTasksOfOneNodeMergeIntoTheNewest() {
        List<LegacyTodoRow> merged = InboxMerger.mergeRowTasks(List.of(
                row("t1", "i1", "n1", "2026-10-01T08:00:00Z"),
                row("t2", "i1", "n1", "2026-10-03T08:00:00Z"),
                row("t3", "i1", "n1", null),
                row("t4", "i1", "n1", "2026-10-02T08:00:00Z")));

        assertThat(merged).extracting(LegacyTodoRow::taskKey).containsExactly("t2");
    }

    @Test
    @DisplayName("C1a-14 different nodes of one instance, and the same node key in another instance, stay separate to-dos, newest first")
    void differentNodesAndInstancesStaySeparate() {
        List<LegacyTodoRow> merged = InboxMerger.mergeRowTasks(List.of(
                row("a1", "i1", "n1", "2026-10-01T08:00:00Z"),
                row("a2", "i1", "n2", "2026-10-02T08:00:00Z"),
                row("a3", "i2", "n1", "2026-10-03T08:00:00Z"),
                row("a4", "i1", "n2", "2026-10-04T08:00:00Z")));

        assertThat(merged).extracting(LegacyTodoRow::taskKey).containsExactly("a4", "a3", "a1");
    }

    @Test
    @DisplayName("RULE-261 row tasks of one (instance, node) are one to-do represented by the newest; other nodes stay separate (moved from TaskPolicyTest with the reader, R-1b P3b)")
    void rowTasksMergePerInstanceAndNode() {
        List<LegacyTodoRow> merged = InboxMerger.mergeRowTasks(List.of(
                row("t1", "P1", "N", "2026-10-01T00:00:00Z"),
                row("t2", "P1", "N", "2026-10-03T00:00:00Z"),
                row("t3", "P1", "N", "2026-10-02T00:00:00Z"),
                row("t4", "P2", "N", "2026-10-04T00:00:00Z"),
                row("t5", "P1", "M", "2026-10-05T00:00:00Z")));

        assertThat(merged).extracting(LegacyTodoRow::taskKey).containsExactly("t5", "t4", "t2");
    }

    @Test
    @DisplayName("nothing to merge gives nothing")
    void empty() {
        assertThat(InboxMerger.mergeRowTasks(List.of())).isEmpty();
    }
}
