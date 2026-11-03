package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AssignmentId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the rules and the timeline need about one employee, read after the employee row is locked: every version row of both tables (superseded
 * ones too, they are the ancestors of re-appended rows), the roots of the person's posts and every command that is not withdrawn.
 */
public record PersonnelHistory(List<VersionRow> employeeRows, List<VersionRow> assignmentRows, List<AssignmentRoot> roots, List<PersonnelCommand> commands,
                               Map<UUID, PersonnelCommand> lookup) {

    public List<VersionRow> currentEmployeeRows() {
        return current(employeeRows);
    }

    /** The current rows of every post of the person. */
    public List<VersionRow> currentAssignmentRows() {
        return current(assignmentRows);
    }

    /** The current rows of one post. */
    public List<VersionRow> currentAssignmentRows(AssignmentId root) {
        return currentAssignmentRows().stream().filter(row -> row.root() != null && new AssignmentId(row.root()).equals(root)).toList();
    }

    public Optional<AssignmentRoot> primaryRoot() {
        return roots.stream().filter(root -> root.kind() == AssignmentKind.PRIMARY).findFirst();
    }

    public Optional<AssignmentRoot> root(AssignmentId id) {
        return roots.stream().filter(root -> root.id().equals(id)).findFirst();
    }

    private static List<VersionRow> current(List<VersionRow> rows) {
        return rows.stream().filter(row -> !row.superseded()).toList();
    }

    public VersionRow row(UUID id) {
        return java.util.stream.Stream.concat(employeeRows.stream(), assignmentRows.stream()).filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }
}
