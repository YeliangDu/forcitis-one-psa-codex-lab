package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.InboxItem;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

record InboxItemResponse(String taskId, String title, @Nullable String nodeName, @Nullable Instant createdAt,
                         boolean claimed, @Nullable Instant deadlineAt, boolean overdue,
                         @Nullable ProcessKey processKey, @Nullable UUID documentId, String category) {

    static InboxItemResponse of(InboxItem item) {
        return new InboxItemResponse(item.taskId(), item.title(), item.nodeName().orElse(null),
                item.createdAt().orElse(null), item.claimed(), item.dueAt().orElse(null),
                item.overdue(), item.processKey().orElse(null), item.documentId().orElse(null), item.category().name());
    }
}
