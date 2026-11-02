package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What one command does to one version table, in the order it must be written (W2 design section 2): the open rows it closes where the change
 * starts, the rows it supersedes, and the rows it inserts.
 */
public record VersionPlan(List<Close> closes, List<UUID> supersede, List<NewRow> inserts) {

    /** An open row that ends on a day: the ordinary way a later change follows the row in force. */
    public record Close(UUID id, LocalDate to) {
    }

    /** A row to insert. correctedFrom is the superseded row it replaces, empty for a row that follows a closed one or fills a gap. */
    public record NewRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom) {
    }

    public static VersionPlan empty() {
        return new VersionPlan(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public boolean isEmpty() {
        return closes.isEmpty() && supersede.isEmpty() && inserts.isEmpty();
    }
}
