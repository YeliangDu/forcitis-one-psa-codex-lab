package com.forcitis.one.platform.approval.application;

import java.util.List;

/**
 * The merged to-do list of one user: new tasks (assigned to the user, or open to a group the user belongs to) plus the old
 * system's tasks, row tasks merged per (instance, node) (RULE-261). totalCount = newCount + legacyCount. legacyAvailable is
 * false when the old database could not be read, so a missing legacy part is never mistaken for "nothing to do".
 */
public record MergedInbox(List<InboxItem> items, int newCount, int legacyCount, int totalCount, boolean legacyAvailable) {

    public MergedInbox {
        items = List.copyOf(items);
    }
}
