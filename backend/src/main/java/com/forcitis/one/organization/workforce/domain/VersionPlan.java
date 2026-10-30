package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What one command does to one version table: the rows it supersedes and the rows it appends, in the order they must be written (W2 design section 2). */
public record VersionPlan(List<UUID> supersede, List<NewRow> inserts) {

    /** A row to inserts. correctedFrom is the superseded row it replaces, empty for a row that fills a gap. */
    public record NewRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom) {
    }

    public static VersionPlan empty() {
        return new VersionPlan(new ArrayList<>(), new ArrayList<>());
    }

    public boolean isEmpty() {
        return supersede.isEmpty() && inserts.isEmpty();
    }
}
