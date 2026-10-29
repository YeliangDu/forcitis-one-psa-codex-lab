package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.HandledPage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** nextCursor is absent on the last page; send it back as cursor to get the page after this one. */
record HandledTaskPageResponse(List<HandledTaskResponse> items, @Nullable String nextCursor) {

    static HandledTaskPageResponse of(HandledPage page) {
        return new HandledTaskPageResponse(page.items().stream().map(HandledTaskResponse::of).toList(), page.nextCursor().orElse(null));
    }
}
