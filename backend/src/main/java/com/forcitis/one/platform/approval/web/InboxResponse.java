package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.MergedInbox;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The counts are of the whole inbox, not of the page; nextCursor is absent on the last page. */
record InboxResponse(List<InboxItemResponse> items, int newCount, int noticeCount, int totalCount,
                     @Nullable String nextCursor) {

    static InboxResponse of(MergedInbox inbox) {
        return new InboxResponse(inbox.items().stream().map(InboxItemResponse::of).toList(), inbox.newCount(),
                inbox.noticeCount(), inbox.totalCount(), inbox.nextCursor().orElse(null));
    }
}
