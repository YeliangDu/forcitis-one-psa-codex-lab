package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ProcessKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record InboxItem(String taskId, String title, Optional<String> nodeName, Optional<Instant> createdAt,
                        boolean claimed, Optional<Instant> dueAt, boolean overdue, Optional<ProcessKey> processKey, Optional<UUID> documentId,
                        InboxCategory category) {
}
