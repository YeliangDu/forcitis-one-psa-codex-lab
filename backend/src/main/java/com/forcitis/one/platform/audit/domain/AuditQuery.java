package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

/**
 * Every filter is optional; from is inclusive, to is exclusive; action matches exactly, actionPrefix by its beginning. after is the
 * position the previous page ended at.
 */
public record AuditQuery(UserId actorId, UserId onBehalfOfId, String action, String actionPrefix, String subjectKey, String sourceAddress,
                         String result, Instant from, Instant to, Position after, int limit) {

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
        if (result != null && !result.equals("SUCCESS") && !result.equals("FAILURE")) {
            throw new IllegalArgumentException("result must be SUCCESS or FAILURE");
        }
    }
}
