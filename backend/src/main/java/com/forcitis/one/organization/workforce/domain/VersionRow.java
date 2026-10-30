package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A row of a version table as the timeline sees it: [from, to) in business dates (to empty means open), the content columns by name, and where
 * it came from. correctedFrom is the row this one was re-appended from, empty for a row nobody replaced. Rows are values: a change is a plan of
 * rows to supersede and rows to inserts, never an edit. A superseded row is no longer a current fact; it stays as the ancestor of the rows that replaced it.
 */
public record VersionRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom,
                         boolean superseded) {

    public VersionRow {
        values = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(from) && (to == null || day.isBefore(to));
    }

    public boolean endsAfter(LocalDate day) {
        return to == null || to.isAfter(day);
    }
}
