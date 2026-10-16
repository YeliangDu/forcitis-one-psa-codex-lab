package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** page is absent for an export, which takes every match up to its own limit. */
public record AuditSearch(UUID actorId, UUID onBehalfOfId, String action, String actionPrefix, AuditSubjectKind subjectKind, UUID subjectId, String sourceAddress,
                          AuditOutcome outcome, Instant from, Instant to, PageRequest page) {

    /** The object a search names: an id needs its kind, because the ids of different kinds are not told apart. */
    AuditSubject subject() {
        if (subjectId == null) {
            return null;
        }
        if (subjectKind == null) {
            throw new IllegalArgumentException("subjectId needs subjectKind");
        }
        return AuditSubject.of(subjectKind, subjectId);
    }

    /** The filter as text for the audit entry about the search or the export; control characters cannot forge a line. */
    String describe() {
        return Stream.of(part("actorId", actorId), part("onBehalfOfId", onBehalfOfId), part("action", blankToNull(action)),
                        part("actionPrefix", blankToNull(actionPrefix)), part("subjectKind", subjectKind), part("subjectId", subjectId),
                        part("sourceAddress", blankToNull(sourceAddress)), part("outcome", outcome), part("from", from), part("to", to))
                .filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private static String part(String name, Object value) {
        return value == null ? null : name + "=" + String.valueOf(value).replaceAll("\\p{Cntrl}", " ");
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
