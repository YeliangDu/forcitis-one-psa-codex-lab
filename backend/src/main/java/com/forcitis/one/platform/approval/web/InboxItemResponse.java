package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.InboxItem;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record InboxItemResponse(String source, String taskId, String title, @Nullable String nodeName, @Nullable Instant createdAt,
                         boolean claimed, @Nullable String detailUrl) {

    static InboxItemResponse of(InboxItem item) {
        return new InboxItemResponse(item.source().name(), item.taskId(), item.title(), item.nodeName().orElse(null),
                item.createdAt().orElse(null), item.claimed(), item.detailUrl().orElse(null));
    }
}
