package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.TaskOverviewPage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** nextCursor is absent on the last page; send it back as cursor to get the page after this one. */
record TaskOverviewPageResponse(List<TaskOverviewResponse> items, @Nullable String nextCursor) {

    static TaskOverviewPageResponse of(TaskOverviewPage page) {
        return new TaskOverviewPageResponse(page.items().stream().map(TaskOverviewResponse::of).toList(), page.nextCursor().orElse(null));
    }
}
