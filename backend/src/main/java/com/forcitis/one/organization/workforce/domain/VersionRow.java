package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A row of a version table as the timeline sees it: [from, to) in business dates (to empty means open), the content columns by name, and where
 * it came from. root is the post an assignment row belongs to (empty on the employee table); relationshipId is the employment relationship (ADR-0086) of an employee or assignment version row. correctedFrom is the row this one was re-appended from, empty for a row nobody replaced. Rows are values: a change is a plan of
 * rows to supersede and rows to inserts, never an edit. A superseded row is no longer a current fact; it stays as the ancestor of the rows that replaced it.
 */
public record VersionRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom,
                         boolean superseded, @Nullable UUID root, @Nullable UUID relationshipId) {

    /** A row of a table with posts but not yet bound to a relationship in the caller's view (org-unit rows, tests). */
    public VersionRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom,
                      boolean superseded, @Nullable UUID root) {
        this(id, from, to, values, source, correctedFrom, superseded, root, null);
    }

    /** The same row with the relationship it belongs to (employee and assignment version rows). */
    public VersionRow inRelationship(@Nullable UUID relationship) {
        return new VersionRow(id, from, to, values, source, correctedFrom, superseded, root, relationship);
    }

    /** A row of a table with no post roots (the employee versions). */
    public VersionRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom, boolean superseded) {
        this(id, from, to, values, source, correctedFrom, superseded, null);
    }

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
