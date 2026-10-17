package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.ListLimits;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditFilter;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The filter of a search and of an export alike (NAMING_STANDARD A-4); an unusable combination is refused at construction. */
public record AuditSearch(UUID actorId, UUID onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, UUID subjectId, String sourceAddress,
                          AuditOutcome outcome, Instant from, Instant to) {

    /** The audit entry about a search or an export carries the filter text after a count; the longest count either writes is the export's. */
    private static final String DETAIL_PREFIX = "returned=" + ListLimits.EXPORT_MAX + " filter=";

    public AuditSearch {
        try {
            toFilter(actorId, onBehalfOfId, action, actionPrefix, subjectKind, subjectId, sourceAddress, outcome, from, to);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException(invalid.getMessage());
        }
        if (!AuditEntry.acceptsText(DETAIL_PREFIX + describe(actorId, onBehalfOfId, action, actionPrefix, subjectKind, subjectId, outcome, from, to))) {
            throw new InvalidRequestException("action and actionPrefix together are too long or look like a secret");
        }
    }

    AuditFilter toFilter() {
        return toFilter(actorId, onBehalfOfId, action, actionPrefix, subjectKind, subjectId, sourceAddress, outcome, from, to);
    }

    /** An id needs its kind, because the ids of different kinds are not told apart. */
    private static AuditFilter toFilter(UUID actorId, UUID onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, UUID subjectId,
                                        String sourceAddress, AuditOutcome outcome, Instant from, Instant to) {
        AuditSubject subject = null;
        if (subjectId != null) {
            if (subjectKind == null) {
                throw new IllegalArgumentException("subjectId needs subjectKind");
            }
            subject = AuditSubject.of(subjectKind, subjectId);
        }
        return new AuditFilter(actorId == null ? null : new UserId(actorId), onBehalfOfId == null ? null : new UserId(onBehalfOfId), blankToNull(action),
                blankToNull(actionPrefix), subjectKind, subject, blankToNull(sourceAddress), outcome, from, to);
    }

    /** The filter as text for the audit entry about the search or the export; control characters cannot forge a line. */
    String describe() {
        return describe(actorId, onBehalfOfId, action, actionPrefix, subjectKind, subjectId, outcome, from, to);
    }

    private static String describe(UUID actorId, UUID onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, UUID subjectId,
                                   AuditOutcome outcome, Instant from, Instant to) {
        return Stream.of(part("actorId", actorId), part("onBehalfOfId", onBehalfOfId), part("action", blankToNull(action)),
                        part("actionPrefix", blankToNull(actionPrefix)), part("subjectKind", subjectKind), part("subjectId", subjectId),
                        part("outcome", outcome), part("from", from), part("to", to))
                .filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private static String part(String name, Object value) {
        return value == null ? null : name + "=" + String.valueOf(value).replaceAll("\\p{Cntrl}", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
