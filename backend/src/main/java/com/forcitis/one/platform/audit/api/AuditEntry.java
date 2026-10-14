package com.forcitis.one.platform.audit.api;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

/**
 * One audit fact. actorId is null for system actions (sync jobs); onBehalfOfId is set only for explicit proxy
 * actions (RULE-270). subjectKey is "{type}:{id}", e.g. "approval-task:42".
 */
public record AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail) {

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectKey, "subjectKey");
    }
}
