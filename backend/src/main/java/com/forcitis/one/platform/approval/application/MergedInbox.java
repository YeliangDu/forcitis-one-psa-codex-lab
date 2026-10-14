package com.forcitis.one.platform.approval.application;

import java.util.List;

/** legacyAvailable is false when the old database could not be read, so a missing legacy part is never mistaken for nothing to do. */
public record MergedInbox(List<InboxItem> items, int newCount, int legacyCount, int totalCount, boolean legacyAvailable) {

    public MergedInbox {
        items = List.copyOf(items);
    }
}
