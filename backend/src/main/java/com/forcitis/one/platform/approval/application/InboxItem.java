package com.forcitis.one.platform.approval.application;

import java.time.Instant;

/**
 * One line of the merged inbox. source is NEW (a task of this system) or LEGACY (a task still in the old system; detailUrl
 * is the old system's page the browser jumps to). claimed is true for new tasks the user already holds.
 */
public record InboxItem(InboxSource source, String taskId, String title, String nodeName, Instant createdAt, boolean claimed,
                        String detailUrl) {
}
