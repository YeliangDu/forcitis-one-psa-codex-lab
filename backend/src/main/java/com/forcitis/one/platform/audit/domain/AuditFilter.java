package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;

/** Every filter is optional; from is inclusive, to is exclusive; action matches exactly, actionPrefix by its beginning. */
public record AuditFilter(UserId actorId, UserId onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, AuditSubject subject,
                          String sourceAddress, AuditOutcome outcome, Instant from, Instant to) {

    public AuditFilter {
        if (subject != null && subjectKind != null && subject.kind() != subjectKind) {
            throw new IllegalArgumentException("subjectKind and the kind of the subject differ");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
