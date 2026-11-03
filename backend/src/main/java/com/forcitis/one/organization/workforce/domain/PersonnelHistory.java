package com.forcitis.one.organization.workforce.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Everything the rules and the timeline need about one employee, read after the employee row is locked: every version row of both tables (superseded
 * ones too, they are the ancestors of re-appended rows; the legal entity rows are the employee's own attribution) and every command that is not withdrawn.
 */
public record PersonnelHistory(List<VersionRow> employeeRows, List<VersionRow> assignmentRows, List<VersionRow> legalEntityRows, List<PersonnelCommand> commands,
                               Map<UUID, PersonnelCommand> lookup) {

    public List<VersionRow> currentEmployeeRows() {
        return current(employeeRows);
    }

    public List<VersionRow> currentAssignmentRows() {
        return current(assignmentRows);
    }

    public List<VersionRow> currentLegalEntityRows() {
        return current(legalEntityRows);
    }

    /** The assignment that covers the day among the current rows, if any. */
    public @Nullable VersionRow assignmentOn(LocalDate day) {
        return currentAssignmentRows().stream().filter(row -> row.covers(day)).findFirst().orElse(null);
    }

    private static List<VersionRow> current(List<VersionRow> rows) {
        return rows.stream().filter(row -> !row.superseded()).toList();
    }

    public VersionRow row(UUID id) {
        return java.util.stream.Stream.of(employeeRows, assignmentRows, legalEntityRows).flatMap(List::stream).filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }
}
