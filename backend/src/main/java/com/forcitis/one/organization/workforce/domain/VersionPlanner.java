package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The supersede-and-inserts mechanism of W2 design section 2 for one version table of one employee. Rows are never edited: a change supersedes the
 * rows whose values change and appends rows covering the same days, so a row split by the change comes back as two rows, the part the change does
 * not touch under its old source. A command sets some columns from its day on until the first later row that sets the same column itself
 * ({@code setters}); columns are followed one by one, so a back-dated transfer does not undo a later one.
 */
public final class VersionPlanner {

    /** The columns a command sets, their values, the rows it may touch and whether it fills a day no row covers (hire, reinstatement). */
    public record Change(LocalDate from, Map<String, Object> values, Predicate<VersionRow> rowFilter, boolean coversEmptyDay) {
    }

    private VersionPlanner() {
    }

    public static VersionPlan apply(List<VersionRow> current, Change change, ChangeSource source, Function<VersionRow, Set<String>> setters, Supplier<UUID> ids) {
        LocalDate day = change.from();
        VersionPlan plan = VersionPlan.empty();
        Set<String> active = new LinkedHashSet<>(change.values().keySet());
        boolean covered = false;
        for (VersionRow row : sorted(current)) {
            if (!row.endsAfter(day)) {
                continue;
            }
            covered |= row.covers(day);
            if (row.from().isAfter(day)) {
                active.removeAll(setters.apply(row));
            }
            if (active.isEmpty()) {
                break;
            }
            if (!change.rowFilter().test(row)) {
                continue;
            }
            Map<String, Object> next = new LinkedHashMap<>(row.values());
            for (String column : active) {
                next.put(column, change.values().get(column));
            }
            if (next.equals(row.values())) {
                continue;
            }
            plan.supersede().add(row.id());
            if (row.from().isBefore(day)) {
                plan.inserts().add(new VersionPlan.NewRow(ids.get(), row.from(), day, row.values(), row.source(), row.id()));
            }
            LocalDate from = row.from().isBefore(day) ? day : row.from();
            plan.inserts().add(new VersionPlan.NewRow(ids.get(), from, row.to(), next, source, row.id()));
        }
        if (!covered && change.coversEmptyDay()) {
            LocalDate end = sorted(current).stream().map(VersionRow::from).filter(f -> f.isAfter(day)).findFirst().orElse(null);
            plan.inserts().add(new VersionPlan.NewRow(ids.get(), day, end, change.values(), source, null));
        }
        return plan;
    }

    /** A separation ends the assignment: rows from the day on are superseded and the row that spans it comes back ending on the day. */
    public static VersionPlan endAt(List<VersionRow> current, LocalDate at, Supplier<UUID> ids) {
        VersionPlan plan = VersionPlan.empty();
        for (VersionRow row : sorted(current)) {
            if (!row.endsAfter(at)) {
                continue;
            }
            plan.supersede().add(row.id());
            if (row.from().isBefore(at)) {
                plan.inserts().add(new VersionPlan.NewRow(ids.get(), row.from(), at, row.values(), row.source(), row.id()));
            }
        }
        return plan;
    }

    /** The reverse of {@link #endAt}: the last row, which ends on the day, comes back open. */
    public static VersionPlan reopenAt(List<VersionRow> current, LocalDate at, Supplier<UUID> ids) {
        VersionPlan plan = VersionPlan.empty();
        List<VersionRow> rows = sorted(current);
        if (!rows.isEmpty() && at.equals(rows.getLast().to())) {
            VersionRow last = rows.getLast();
            plan.supersede().add(last.id());
            plan.inserts().add(new VersionPlan.NewRow(ids.get(), last.from(), null, last.values(), last.source(), last.id()));
        }
        return plan;
    }

    /**
     * Takes a command's effect out again (a withdrawal): the columns it set go back to what they were the day before, up to the first later row
     * that sets them itself; rows the command wrote come back under the source of the row they were split from, rows it only inserted are
     * superseded and not replaced. {@code byId} reaches rows that are no longer current.
     */
    public static VersionPlan revert(List<VersionRow> current, Function<UUID, VersionRow> byId, PersonnelCommandId commandId, LocalDate day, Set<String> columns,
                                      Predicate<VersionRow> rowFilter, Function<VersionRow, Set<String>> setters, Supplier<UUID> ids) {
        VersionPlan plan = VersionPlan.empty();
        List<VersionRow> rows = sorted(current);
        VersionRow before = rows.stream().filter(r -> r.covers(day.minusDays(1))).findFirst().orElse(null);
        Set<String> active = new LinkedHashSet<>(columns);
        for (VersionRow row : rows) {
            if (!row.endsAfter(day)) {
                continue;
            }
            boolean mine = row.source().isCommand(commandId);
            if (row.from().isAfter(day) && !mine) {
                active.removeAll(setters.apply(row));
            }
            if (active.isEmpty() && !mine) {
                break;
            }
            Map<String, Object> next = new LinkedHashMap<>(row.values());
            if (before != null && rowFilter.test(row)) {
                for (String column : active) {
                    next.put(column, before.values().get(column));
                }
            }
            if (!mine && next.equals(row.values())) {
                continue;
            }
            plan.supersede().add(row.id());
            VersionRow origin = row.correctedFrom() == null ? null : byId.apply(row.correctedFrom());
            if (mine && origin == null) {
                continue;
            }
            ChangeSource source = origin != null && mine ? origin.source() : row.source();
            plan.inserts().add(new VersionPlan.NewRow(ids.get(), row.from(), row.to(), next, source, row.id()));
        }
        return plan;
    }

    private static List<VersionRow> sorted(List<VersionRow> rows) {
        List<VersionRow> copy = new ArrayList<>(rows);
        copy.sort(Comparator.comparing(VersionRow::from));
        return copy;
    }
}
