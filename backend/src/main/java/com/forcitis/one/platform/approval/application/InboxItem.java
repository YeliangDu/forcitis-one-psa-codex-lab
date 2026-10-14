package com.forcitis.one.platform.approval.application;

import java.time.Instant;

/** detailUrl is the old system's page the browser jumps to (LEGACY items only). */
public record InboxItem(InboxSource source, String taskId, String title, String nodeName, Instant createdAt, boolean claimed,
                        String detailUrl) {
}
