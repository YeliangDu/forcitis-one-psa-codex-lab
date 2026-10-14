package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.MergedInbox;
import java.util.List;
import org.springframework.lang.Nullable;

/** The counts are of the whole inbox, not of the page; nextCursor is absent on the last page. legacyAvailable is false when the old database could not be read, so a missing legacy part is never mistaken for nothing to do. */
record InboxResponse(List<InboxItemResponse> items, int newCount, int legacyCount, int noticeCount, int totalCount, boolean legacyAvailable,
                     @Nullable String nextCursor) {

    static InboxResponse of(MergedInbox inbox) {
        return new InboxResponse(inbox.items().stream().map(InboxItemResponse::of).toList(), inbox.newCount(), inbox.legacyCount(),
                inbox.noticeCount(), inbox.totalCount(), inbox.legacyAvailable(), inbox.nextCursor().orElse(null));
    }
}
