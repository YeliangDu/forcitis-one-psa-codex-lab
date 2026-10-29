package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.application.InboxItem;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** processName is what a person calls the process, from its definition; a client shows it as given and keeps no table of its own. */
record InboxItemResponse(String taskId, String title, @Nullable String nodeName, @Nullable Instant createdAt,
                         boolean claimed, @Nullable Instant deadlineAt, boolean overdue,
                         @Nullable ProcessKey processKey, @Nullable String processName, @Nullable UUID documentId, String category) {

    static InboxItemResponse of(InboxItem item) {
        return new InboxItemResponse(item.taskId(), item.title(), item.nodeName().orElse(null),
                item.createdAt().orElse(null), item.claimed(), item.dueAt().orElse(null),
                item.overdue(), item.processKey().orElse(null), item.processName().map(ProcessName::value).orElse(null),
                item.documentId().orElse(null), item.category().name());
    }
}
