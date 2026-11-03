package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PagedRunTest {

    private static List<PermissionAdjustmentId> ids(int count) {
        List<PermissionAdjustmentId> all = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            all.add(new PermissionAdjustmentId(new UUID(0, i)));
        }
        return all;
    }

    private static List<PermissionAdjustmentId> after(List<PermissionAdjustmentId> all, @Nullable PermissionAdjustmentId cursor, int limit) {
        return all.stream().filter(id -> cursor == null || id.value().compareTo(cursor.value()) > 0).limit(limit).toList();
    }

    @Test
    @DisplayName("#730 S2: more pending items than one page are all visited exactly once")
    void everyItemBeyondOnePageIsVisited() {
        List<PermissionAdjustmentId> all = ids(PagedRun.PAGE * 2 + 1);
        List<PermissionAdjustmentId> seen = new ArrayList<>();
        PagedRun.forEachPending((cursor, limit) -> after(all, cursor, limit), seen::add);
        assertThat(seen).containsExactlyElementsOf(all);
    }

    @Test
    @DisplayName("#730 S2: a full page of items that stay pending does not hide the ones behind it")
    void stuckItemsDoNotBlockTheRest() {
        List<PermissionAdjustmentId> all = ids(PagedRun.PAGE + 5);
        List<PermissionAdjustmentId> seen = new ArrayList<>();
        PagedRun.forEachPending((cursor, limit) -> after(all, cursor, limit), seen::add);
        assertThat(seen).hasSize(PagedRun.PAGE + 5).endsWith(all.getLast());
    }
}
