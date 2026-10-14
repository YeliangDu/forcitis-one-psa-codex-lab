package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.MergedInbox;
import java.util.List;

/** legacyAvailable is false when the old database could not be read, so a missing legacy part is never mistaken for nothing to do. */
record InboxResponse(List<InboxItemResponse> items, int newCount, int legacyCount, int totalCount, boolean legacyAvailable) {

    static InboxResponse of(MergedInbox inbox) {
        return new InboxResponse(inbox.items().stream().map(InboxItemResponse::of).toList(), inbox.newCount(), inbox.legacyCount(),
                inbox.totalCount(), inbox.legacyAvailable());
    }
}
