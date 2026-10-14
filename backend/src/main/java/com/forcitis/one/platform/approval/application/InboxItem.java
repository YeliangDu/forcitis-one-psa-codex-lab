package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** detailUrl is the old system's page the browser jumps to (LEGACY items only). */
public record InboxItem(InboxSource source, String taskId, String title, @Nullable String nodeName, @Nullable Instant createdAt, boolean claimed,
                        @Nullable String detailUrl) {
}
