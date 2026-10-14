package com.forcitis.one.platform.audit.api;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * actorId is null for system actions; onBehalfOfId only for explicit proxy actions; subjectKey is "{type}:{id}". The source address,
 * outcome and login method are structured columns for entries that have them (logins and session ends): detail must not carry them.
 * Nothing here may carry a secret or personal data (L3 / L4, ADR-0026 section 7): text that looks like one is refused.
 */
public record AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail,
                         @Nullable String sourceAddress, @Nullable AuditOutcome outcome, @Nullable String loginMethod,
                         @Nullable String changeReason, @Nullable String beforeText, @Nullable String afterText) {
    private static final int MAX_TEXT = 2000;
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(password|passphrase|secret|totp|api[_-]?key|token|authorization)\\s*[=:]");

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectKey, "subjectKey");
        for (String text : new String[] {detail, changeReason, beforeText, afterText}) {
            if (text != null && (text.length() > MAX_TEXT || SECRET.matcher(text).find())) {
                throw new IllegalArgumentException("an audit text is at most " + MAX_TEXT + " characters and carries no secret");
            }
        }
    }

    public AuditEntry(String action, UserId actorId, UserId onBehalfOfId, String subjectKey, String detail) {
        this(action, actorId, onBehalfOfId, subjectKey, detail, null, null, null, null, null, null);
    }

    public AuditEntry withTrace(String sourceAddress, AuditOutcome outcome, String loginMethod) {
        return new AuditEntry(action, actorId, onBehalfOfId, subjectKey, detail, sourceAddress, outcome, loginMethod, changeReason, beforeText,
                afterText);
    }

    public AuditEntry withChange(String reason, String before, String after) {
        return new AuditEntry(action, actorId, onBehalfOfId, subjectKey, detail, sourceAddress, outcome, loginMethod, reason, before, after);
    }
}
