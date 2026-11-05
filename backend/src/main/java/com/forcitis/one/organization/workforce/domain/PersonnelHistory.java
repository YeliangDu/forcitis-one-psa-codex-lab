package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AssignmentId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Everything the rules and the timeline need about one employee, read after the employee row is locked: every version row of both tables (superseded
 * ones too, they are the ancestors of re-appended rows; the legal entity rows are the employee's own attribution), the roots of the person's posts and every command that is not withdrawn.
 */
public record PersonnelHistory(List<VersionRow> employeeRows, List<VersionRow> assignmentRows, List<AssignmentRoot> roots, List<VersionRow> legalEntityRows, List<PersonnelCommand> commands,
                               Map<UUID, PersonnelCommand> lookup, List<RelationshipRow> relationships) {

    /** A history of an employee with no employment relationship rows in view (tests of the rules that do not depend on them). */
    public PersonnelHistory(List<VersionRow> employeeRows, List<VersionRow> assignmentRows, List<AssignmentRoot> roots, List<VersionRow> legalEntityRows, List<PersonnelCommand> commands,
                            Map<UUID, PersonnelCommand> lookup) {
        this(employeeRows, assignmentRows, roots, legalEntityRows, commands, lookup, List.of());
    }

    /**
     * The history as one relationship sees it: its employee and assignment rows and its commands; the roots, the legal entity rows and the lookup stay whole
     * (a post can move from one relationship to the next, the attribution is the person's). The rules and the plans of a command run over this view.
     */
    public PersonnelHistory within(UUID relationship) {
        return new PersonnelHistory(employeeRows.stream().filter(r -> relationship.equals(r.relationshipId())).toList(),
                assignmentRows.stream().filter(r -> relationship.equals(r.relationshipId())).toList(), roots, legalEntityRows,
                commands.stream().filter(c -> c.employmentRelationshipId() != null && relationship.equals(c.employmentRelationshipId().value())).toList(), lookup, relationships);
    }

    public Optional<RelationshipRow> relationship(UUID id) {
        return relationships.stream().filter(r -> r.id().value().equals(id)).findFirst();
    }

    /** The current employee rows of one relationship. */
    public List<VersionRow> currentEmployeeRows(UUID relationship) {
        return currentEmployeeRows().stream().filter(row -> relationship.equals(row.relationshipId())).toList();
    }

    /** The current rows of every post that belong to one relationship. */
    public List<VersionRow> currentAssignmentRowsOf(UUID relationship) {
        return currentAssignmentRows().stream().filter(row -> relationship.equals(row.relationshipId())).toList();
    }

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

    public List<VersionRow> currentLegalEntityRows() {
        return current(legalEntityRows);
    }

    /** The primary assignment that covers the day among the current rows, if any: a concurrent post is nobody's reporting line or unit (#711). */
    public @Nullable VersionRow assignmentOn(LocalDate day) {
        UUID primary = primaryRoot().map(root -> root.id().value()).orElse(null);
        return currentAssignmentRows().stream().filter(row -> row.root() == null || row.root().equals(primary)).filter(row -> row.covers(day)).findFirst().orElse(null);
    }

    private static List<VersionRow> current(List<VersionRow> rows) {
        return rows.stream().filter(row -> !row.superseded()).toList();
    }

    public VersionRow row(UUID id) {
        return java.util.stream.Stream.of(employeeRows, assignmentRows, legalEntityRows).flatMap(List::stream).filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }
}
