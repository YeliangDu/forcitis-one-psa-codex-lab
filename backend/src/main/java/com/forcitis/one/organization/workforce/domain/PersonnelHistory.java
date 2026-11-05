package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
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
    public PersonnelHistory within(EmploymentRelationshipId relationship) {
        return new PersonnelHistory(employeeRows.stream().filter(r -> relationship.equals(r.relationshipId())).toList(),
                assignmentRows.stream().filter(r -> relationship.equals(r.relationshipId())).toList(), roots, legalEntityRows,
                commands.stream().filter(c -> relationship.equals(c.employmentRelationshipId())).toList(), lookup, relationships);
    }

    /**
     * The history as the relationship sees it when the primary post moves into it (design 6.3, item 12): the primary root's rows of any relationship are shown as the
     * relationship's, so the plan closes the old version on the day and writes the next one in the same root. Stored rows keep the relationship they were written with.
     */
    public PersonnelHistory withPrimaryIn(EmploymentRelationshipId relationship) {
        UUID primary = primaryRoot().map(root -> root.id().value()).orElse(null);
        List<VersionRow> rows = assignmentRows.stream().filter(r -> relationship.equals(r.relationshipId()) || primary != null && primary.equals(r.root()))
                .map(r -> primary != null && primary.equals(r.root()) ? r.inRelationship(relationship) : r).toList();
        return new PersonnelHistory(employeeRows.stream().filter(r -> relationship.equals(r.relationshipId())).toList(), rows, roots, legalEntityRows,
                commands.stream().filter(c -> relationship.equals(c.employmentRelationshipId())).toList(), lookup, relationships);
    }

    /**
     * Whether the person goes from not in service to in service with this relationship on the day (EmployeeHired, EmployeeReinstated): no other relationship was in
     * service on the day before, and none that came earlier is in service on the day itself; an employer change in continuous service (a predecessor) is no entry.
     * The relationships are in the order they were made.
     */
    public boolean personEnters(EmploymentRelationshipId relationship, LocalDate day) {
        RelationshipRow own = relationship(relationship).orElse(null);
        if (own != null && own.predecessorId() != null) {
            return false;
        }
        boolean before = true;
        for (RelationshipRow other : relationships) {
            if (other.id().equals(relationship)) {
                before = false;
                continue;
            }
            boolean onDayBefore = inService(other.id(), day.minusDays(1));
            boolean onDay = inService(other.id(), day);
            if (onDayBefore || before && onDay) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the person goes out of service when this relationship ends on the day (EmployeeSeparated): no other relationship is in service on the day. An employer change
     * in continuous service (a hire naming this one as predecessor) is checked by {@code PersonnelCommand#personTransition}, not here.
     */
    public boolean personLeaves(EmploymentRelationshipId relationship, LocalDate day) {
        return relationships.stream().noneMatch(r -> !r.id().equals(relationship) && inService(r.id(), day));
    }

    private boolean inService(EmploymentRelationshipId relationship, LocalDate day) {
        return currentEmployeeRows(relationship).stream().anyMatch(r -> r.covers(day) && "EMPLOYED".equals(r.values().get(CommandEffects.EMPLOYMENT)));
    }

    public Optional<RelationshipRow> relationship(EmploymentRelationshipId id) {
        return relationships.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** The current employee rows of one relationship. */
    public List<VersionRow> currentEmployeeRows(EmploymentRelationshipId relationship) {
        return currentEmployeeRows().stream().filter(row -> relationship.equals(row.relationshipId())).toList();
    }

    /** The current rows of every post that belong to one relationship. */
    public List<VersionRow> currentAssignmentRowsOf(EmploymentRelationshipId relationship) {
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
