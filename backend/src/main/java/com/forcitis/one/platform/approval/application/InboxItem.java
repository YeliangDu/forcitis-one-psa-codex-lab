package com.forcitis.one.platform.approval.application;

import java.time.Instant;
import java.util.Optional;

/** detailUrl is the old system's page the browser jumps to (LEGACY items only). */
public record InboxItem(InboxSource source, String taskId, String title, Optional<String> nodeName, Optional<Instant> createdAt,
                        boolean claimed, Optional<String> detailUrl) {
}
