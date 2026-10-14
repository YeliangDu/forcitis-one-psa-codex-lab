package com.forcitis.one.platform.audit.api;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

/** actorId is null for system actions; onBehalfOfId only for explicit proxy actions; subjectKey is "{type}:{id}". */
public record AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail) {
    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectKey, "subjectKey");
    }
}
