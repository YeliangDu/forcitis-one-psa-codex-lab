package com.forcitis.one.platform.approval.application;

import java.util.List;
import java.util.Optional;

/** The counts are of the whole inbox, not of the page; nextCursor is empty on the last page. */
public record MergedInbox(List<InboxItem> items, int newCount, int noticeCount, int totalCount,
                          Optional<String> nextCursor) {

    public MergedInbox {
        items = List.copyOf(items);
    }
}
