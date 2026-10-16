package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;

/**
 * Every filter is optional; from is inclusive, to is exclusive; action matches exactly, actionPrefix by its beginning. after is the
 * position the previous page ended at.
 */
public record AuditQuery(UserId actorId, UserId onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, AuditSubject subject, String sourceAddress,
                         AuditOutcome outcome, Instant from, Instant to, Position after, int limit) {

    public static final int MAX_LIMIT = 200;

    public record Position(Instant createdAt, AuditEntryId entryId) {
    }

    public AuditQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be 1.." + MAX_LIMIT);
        }
        if (subject != null && subjectKind != null && subject.kind() != subjectKind) {
            throw new IllegalArgumentException("subjectKind and the kind of the subject differ");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
