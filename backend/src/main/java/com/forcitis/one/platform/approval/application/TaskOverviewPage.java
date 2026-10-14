package com.forcitis.one.platform.approval.application;

import java.util.List;
import java.util.Optional;

/** nextCursor is empty on the last page. */
public record TaskOverviewPage(List<TaskOverview> items, Optional<String> nextCursor) {

    public TaskOverviewPage {
        items = List.copyOf(items);
    }
}
