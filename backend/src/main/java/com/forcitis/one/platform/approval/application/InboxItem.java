package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * detailUrl is the old system's page the browser jumps to (LEGACY items only). overdue marks a new task past its deadline; the old
 * system's tasks are never marked (D-1a-117).
 */
public record InboxItem(InboxSource source, String taskId, String title, Optional<String> nodeName, Optional<Instant> createdAt,
                        boolean claimed, Optional<String> detailUrl, Optional<Instant> dueAt, boolean overdue, Optional<ProcessKey> processKey, Optional<UUID> documentId,
                        InboxCategory category) {
}
