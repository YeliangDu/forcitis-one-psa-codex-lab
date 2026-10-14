package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

/** Every filter is optional; from is inclusive, to is exclusive. after is the position the previous page ended at. */
public record AuditQuery(UserId actorId, String action, String subjectKey, Instant from, Instant to, Position after, int limit) {

    public static final int MAX_LIMIT = 200;

    public record Position(Instant createdAt, AuditEntryId entryId) {
    }

    public AuditQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
