package com.forcitis.one.platform.approval.application;

import java.util.List;
import java.util.Optional;

/** The counts are of the whole inbox, not of the page; nextCursor is empty on the last page. legacyAvailable is false when the old database could not be read, so a missing legacy part is never mistaken for nothing to do. */
public record MergedInbox(List<InboxItem> items, int newCount, int legacyCount, int noticeCount, int totalCount, boolean legacyAvailable,
                          Optional<String> nextCursor) {

    public MergedInbox {
        items = List.copyOf(items);
    }
}
