package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Walks all pending ids page by page with an id cursor, so an item that stays pending or keeps failing cannot hide the ones behind it. */
final class PagedRun {

    static final int PAGE = 100;

    private PagedRun() {
    }

    static void forEachPending(BiFunction<@Nullable PermissionAdjustmentId, Integer, List<PermissionAdjustmentId>> page, Consumer<PermissionAdjustmentId> step) {
        @Nullable PermissionAdjustmentId after = null;
        List<PermissionAdjustmentId> ids;
        do {
            ids = page.apply(after, PAGE);
            ids.forEach(step);
            if (!ids.isEmpty()) {
                after = ids.getLast();
            }
        } while (ids.size() == PAGE);
    }
}
