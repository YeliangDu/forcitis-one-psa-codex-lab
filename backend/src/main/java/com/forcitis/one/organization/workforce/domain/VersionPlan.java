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

    /** A row to insert. correctedFrom is the superseded row it replaces, empty for a row that follows a closed one or fills a gap. root is the post it belongs to, empty on the employee table and for the primary post's first row (the store names it). */
    public record NewRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom,
                         @Nullable UUID root) {

        /** A row of the post the plan's other rows belong to, or of the employee table. */
        public NewRow(UUID id, LocalDate from, @Nullable LocalDate to, Map<String, Object> values, ChangeSource source, @Nullable UUID correctedFrom) {
            this(id, from, to, values, source, correctedFrom, null);
        }
    }

    public static VersionPlan empty() {
        return new VersionPlan(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /** The same plan with every inserted row on the post: a plan is made over the rows of one post, so the planner leaves the root to the caller. */
    public VersionPlan withRoot(UUID root) {
        List<NewRow> stamped = inserts.stream().map(r -> new NewRow(r.id(), r.from(), r.to(), r.values(), r.source(), r.correctedFrom(), root)).toList();
        return new VersionPlan(new ArrayList<>(closes), new ArrayList<>(supersede), new ArrayList<>(stamped));
    }

    /** The rows of both plans in one plan: closes first, then supersedes, then inserts, each in the order given. */
    public VersionPlan and(VersionPlan other) {
        VersionPlan merged = empty();
        for (VersionPlan plan : List.of(this, other)) {
            merged.closes().addAll(plan.closes());
            merged.supersede().addAll(plan.supersede());
            merged.inserts().addAll(plan.inserts());
        }
        return merged;
    }

    public boolean isEmpty() {
        return closes.isEmpty() && supersede.isEmpty() && inserts.isEmpty();
    }
}
