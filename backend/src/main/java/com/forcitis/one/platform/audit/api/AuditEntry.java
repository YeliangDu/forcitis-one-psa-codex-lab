package com.forcitis.one.platform.audit.api;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * actorId is null for system actions; onBehalfOfId only for explicit proxy actions; subjectKey is "{type}:{id}". The last three
 * are structured columns for entries that have them (logins and session ends): detail must not carry them.
 */
public record AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail,
                         @Nullable String sourceAddress, @Nullable AuditOutcome outcome, @Nullable String loginMethod) {
    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectKey, "subjectKey");
    }

    public AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail) {
        this(action, actorId, onBehalfOfId, subjectKey, detail, null, null, null);
    }

    public AuditEntry withTrace(String sourceAddress, AuditOutcome outcome, String loginMethod) {
        return new AuditEntry(action, actorId, onBehalfOfId, subjectKey, detail, sourceAddress, outcome, loginMethod);
    }
}
