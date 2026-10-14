package com.forcitis.one.platform.approval.application;

import java.util.List;
import java.util.Optional;

/** nextCursor is empty on the last page. */
public record HandledPage(List<HandledTask> items, Optional<String> nextCursor) {

    public HandledPage {
        items = List.copyOf(items);
    }
}
