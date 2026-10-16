package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Every filter is optional; subjectId needs its subjectKind, because the ids of different kinds are not told apart; from is inclusive, to is exclusive; action matches exactly, actionPrefix by its beginning. after is the
 * position the previous page ended at.
 */
public record AuditQuery(UserId actorId, UserId onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, UUID subjectId, String sourceAddress,
                         AuditOutcome outcome, Instant from, Instant to, Position after, int limit) {

    public static final int MAX_LIMIT = 200;

    public record Position(Instant createdAt, AuditEntryId entryId) {
    }

    public AuditQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT);
        }
        if (subjectId != null && subjectKind == null) {
            throw new IllegalArgumentException("subjectId needs subjectKind");
        }
        if (subjectId != null && !subjectKind.hasObject()) {
            throw new IllegalArgumentException(subjectKind + " has no object, so no id");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
