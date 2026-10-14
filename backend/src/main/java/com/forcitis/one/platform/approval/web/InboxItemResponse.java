package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.InboxItem;
import java.time.Instant;
import org.springframework.lang.Nullable;

record InboxItemResponse(String source, String taskId, String title, @Nullable String nodeName, @Nullable Instant createdAt,
                         boolean claimed, @Nullable String detailUrl, @Nullable Instant deadlineAt, boolean overdue,
                         @Nullable String processKey, @Nullable String documentId, String category) {

    static InboxItemResponse of(InboxItem item) {
        return new InboxItemResponse(item.source().name(), item.taskId(), item.title(), item.nodeName().orElse(null),
                item.createdAt().orElse(null), item.claimed(), item.detailUrl().orElse(null), item.dueAt().orElse(null),
                item.overdue(), item.processKey().map(Enum::name).orElse(null), item.documentId().orElse(null), item.category().name());
    }
}
