package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The tree rules of org-structure commands, judged on the whole timeline and not only on one day (one-design ruling on #906): a command is checked
 * against every row already entered, including those that start later, so two commands that are fine alone but wrong together are caught by the
 * second one. {@code units} holds the current version rows of every org unit.
 */
public final class OrgTree {
    private static final String ACTIVE = "ACTIVE";

    private final Map<UUID, List<VersionRow>> units;

    public OrgTree(Map<UUID, List<VersionRow>> units) {
        this.units = units;
    }

    /** The unit is in force on the day (a current row covers it and is ACTIVE). */
    public boolean activeOn(UUID unit, LocalDate day) {
        return rowsOf(unit).stream().anyMatch(row -> row.covers(day) && ACTIVE.equals(row.values().get(OrgEffects.LIFECYCLE)));
    }

    /** The unit is ACTIVE on the day and on every day after: something placed below it from the day on will not be left under a revoked unit. */
    public boolean activeFrom(UUID unit, LocalDate day) {
        List<VersionRow> rows = rowsOf(unit).stream().filter(row -> row.endsAfter(day)).toList();
        return activeOn(unit, day) && rows.stream().allMatch(row -> ACTIVE.equals(row.values().get(OrgEffects.LIFECYCLE)));
    }

    /** Whether some other unit has the unit as its parent while ACTIVE on the day or later. */
    public boolean hasActiveChildFrom(UUID unit, LocalDate day) {
        for (Map.Entry<UUID, List<VersionRow>> other : units.entrySet()) {
            if (other.getKey().equals(unit)) {
                continue;
            }
            for (VersionRow row : other.getValue()) {
                if (row.endsAfter(day) && ACTIVE.equals(row.values().get(OrgEffects.LIFECYCLE)) && unit.equals(row.values().get(OrgEffects.PARENT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Refuses a plan for the unit that makes it its own ancestor on some day. The tree is read on the day of the change and on every later day a row of any
     * unit starts, with the unit's own rows replaced by what the plan leaves.
     */
    public void requireNoCycle(UUID unit, LocalDate from, VersionPlan plan) {
        Map<UUID, List<VersionRow>> after = new HashMap<>(units);
        after.put(unit, applied(rowsOf(unit), plan));
        Set<LocalDate> days = new TreeSet<>();
        days.add(from);
        after.values().forEach(rows -> rows.stream().map(VersionRow::from).filter(d -> d.isAfter(from)).forEach(days::add));
        for (LocalDate day : days) {
            Set<UUID> seen = new HashSet<>();
            UUID cursor = unit;
            while (cursor != null && seen.add(cursor)) {
                cursor = parentOn(after.getOrDefault(cursor, List.of()), day);
            }
            if (unit.equals(cursor)) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE,
                        "the org unit would be its own ancestor on " + day);
            }
        }
    }

    private static @Nullable UUID parentOn(List<VersionRow> rows, LocalDate day) {
        for (VersionRow row : rows) {
            if (row.covers(day)) {
                return (UUID) row.values().get(OrgEffects.PARENT);
            }
        }
        return null;
    }

    /** The rows as they stand once the plan is written. */
    static List<VersionRow> applied(List<VersionRow> rows, VersionPlan plan) {
        List<VersionRow> result = new ArrayList<>();
        for (VersionRow row : rows) {
            if (plan.supersede().contains(row.id())) {
                continue;
            }
            VersionPlan.Close close = plan.closes().stream().filter(c -> c.id().equals(row.id())).findFirst().orElse(null);
            result.add(close == null ? row : new VersionRow(row.id(), row.from(), close.to(), row.values(), row.source(), row.correctedFrom(), false));
        }
        for (VersionPlan.NewRow row : plan.inserts()) {
            result.add(new VersionRow(row.id(), row.from(), row.to(), row.values(), row.source(), row.correctedFrom(), false));
        }
        return result;
    }

    private List<VersionRow> rowsOf(UUID unit) {
        return units.getOrDefault(unit, List.of());
    }
}
