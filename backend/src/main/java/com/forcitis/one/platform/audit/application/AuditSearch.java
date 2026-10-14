package com.forcitis.one.platform.audit.application;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record AuditSearch(Long actorId, String action, String subjectKey, Instant from, Instant to, int page, int limit) {

    /** The filter as text for the audit entry about the search or the export; control characters cannot forge a line. */
    String describe() {
        return Stream.of(part("actorId", actorId), part("action", blankToNull(action)), part("subjectKey", blankToNull(subjectKey)),
                part("from", from), part("to", to)).filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private static String part(String name, Object value) {
        return value == null ? null : name + "=" + String.valueOf(value).replaceAll("\\p{Cntrl}", " ");
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
