package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** processName is the name of the process definition, sent with the item so that a client keeps no table of processes (REG-4). */
public record InboxItem(String taskId, String title, Optional<String> nodeName, Optional<Instant> createdAt,
                        boolean claimed, Optional<Instant> dueAt, boolean overdue, Optional<ProcessKey> processKey,
                        Optional<ProcessName> processName, Optional<UUID> documentId, InboxCategory category) {
}
