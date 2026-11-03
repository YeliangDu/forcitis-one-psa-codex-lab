package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.AssignmentRoot;
import com.forcitis.one.organization.workforce.domain.CommandEffects;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.organization.workforce.domain.VersionTable;
import com.forcitis.one.organization.workforce.domain.VersionPlanner;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Plans what a command does to the version tables (employee, assignment and the employee's own legal entity attribution), and what taking it back does; the plans are only values, the use case writes them. */
@Component
class PersonnelPlans {
    private final IdGenerator ids;

    PersonnelPlans(IdGenerator ids) {
        this.ids = ids;
    }

    /** Both plans of a command, in the order they are written. */
    Map<VersionTable, VersionPlan> forEntry(PersonnelCommand command, PersonnelHistory history, AuditEntryId auditEntryId) {
        ChangeSource source = ChangeSource.ofCommand(command.id(), auditEntryId);
        Map<VersionTable, VersionPlan> plans = new java.util.EnumMap<>(VersionTable.class);
        Map<String, Object> employee = CommandEffects.employeeValues(command);
        if (!employee.isEmpty()) {
            plans.put(VersionTable.EMPLOYEE, VersionPlanner.apply(history.currentEmployeeRows(), new VersionPlanner.Change(command.effectiveDate(), employee,
                    CommandEffects.employeeFilter(command), CommandEffects.coversEmptyDay(command)), source, setters(history, VersionTable.EMPLOYEE), ids::next));
        }
        Map<String, Object> legalEntity = CommandEffects.legalEntityValues(command);
        if (!legalEntity.isEmpty()) {
            plans.put(VersionTable.LEGAL_ENTITY, VersionPlanner.apply(history.currentLegalEntityRows(), new VersionPlanner.Change(command.effectiveDate(), legalEntity,
                    row -> true, true), source, setters(history, VersionTable.LEGAL_ENTITY), ids::next));
        }
        if (CommandEffects.endsAssignment(command)) {
            plans.put(VersionTable.ASSIGNMENT, everyPost(history, rows -> VersionPlanner.endAt(rows, command.effectiveDate(), ids::next)));
        } else if (command.kind() == PersonnelCommandKind.START_CONCURRENT) {
            UUID root = ids.next();
            VersionPlan start = VersionPlan.empty();
            start.inserts().add(new VersionPlan.NewRow(ids.next(), command.effectiveDate(), null, CommandEffects.assignmentValues(command), source, null, root));
            plans.put(VersionTable.ASSIGNMENT, start);
        } else if (command.kind() == PersonnelCommandKind.END_CONCURRENT) {
            plans.put(VersionTable.ASSIGNMENT, VersionPlanner.endAt(postRows(command, history), command.effectiveDate(), ids::next).withRoot(postOf(command)));
        } else {
            Map<String, Object> assignment = CommandEffects.assignmentValues(command);
            if (!assignment.isEmpty()) {
                VersionPlan plan = VersionPlanner.apply(postRows(command, history), new VersionPlanner.Change(command.effectiveDate(), assignment, row -> true,
                        CommandEffects.coversEmptyDay(command)), source, setters(history, VersionTable.ASSIGNMENT), ids::next);
                plans.put(VersionTable.ASSIGNMENT, command.targetAssignmentId() == null ? plan : plan.withRoot(postOf(command)));
            }
        }
        return plans;
    }

    /** Both plans that take the command's effect out again. */
    Map<VersionTable, VersionPlan> forWithdrawal(PersonnelCommand command, PersonnelHistory history) {
        Map<VersionTable, VersionPlan> plans = new java.util.EnumMap<>(VersionTable.class);
        Map<String, Object> employee = CommandEffects.employeeValues(command);
        if (!employee.isEmpty()) {
            plans.put(VersionTable.EMPLOYEE, VersionPlanner.revert(history.currentEmployeeRows(), id -> history.row(id), command.id(), command.effectiveDate(),
                    employee.keySet(), CommandEffects.employeeFilter(command), setters(history, VersionTable.EMPLOYEE), ids::next));
        }
        Map<String, Object> legalEntity = CommandEffects.legalEntityValues(command);
        if (!legalEntity.isEmpty()) {
            plans.put(VersionTable.LEGAL_ENTITY, VersionPlanner.revert(history.currentLegalEntityRows(), id -> history.row(id), command.id(), command.effectiveDate(),
                    legalEntity.keySet(), row -> true, setters(history, VersionTable.LEGAL_ENTITY), ids::next));
        }
        if (CommandEffects.endsAssignment(command)) {
            plans.put(VersionTable.ASSIGNMENT, everyPost(history, rows -> VersionPlanner.reopenAt(rows, command.effectiveDate(), ids::next)));
        } else if (command.kind() == PersonnelCommandKind.END_CONCURRENT) {
            plans.put(VersionTable.ASSIGNMENT, VersionPlanner.reopenAt(postRows(command, history), command.effectiveDate(), ids::next).withRoot(postOf(command)));
        } else if (command.kind() == PersonnelCommandKind.START_CONCURRENT) {
            startedPost(command, history).ifPresent(root -> plans.put(VersionTable.ASSIGNMENT, VersionPlanner.revert(history.currentAssignmentRows(root), id -> history.row(id),
                    command.id(), command.effectiveDate(), CommandEffects.assignmentValues(command).keySet(), row -> true, setters(history, VersionTable.ASSIGNMENT),
                    ids::next).withRoot(root.value())));
        } else {
            Map<String, Object> assignment = CommandEffects.assignmentValues(command);
            if (!assignment.isEmpty()) {
                VersionPlan plan = VersionPlanner.revert(postRows(command, history), id -> history.row(id), command.id(), command.effectiveDate(), assignment.keySet(),
                        row -> true, setters(history, VersionTable.ASSIGNMENT), ids::next);
                plans.put(VersionTable.ASSIGNMENT, command.targetAssignmentId() == null ? plan : plan.withRoot(postOf(command)));
            }
        }
        return plans;
    }

    /** The plan over each post of the person, one after the other, in one plan: a separation ends, and its withdrawal reopens, every post. */
    private VersionPlan everyPost(PersonnelHistory history, Function<List<VersionRow>, VersionPlan> planOver) {
        VersionPlan all = VersionPlan.empty();
        for (AssignmentRoot root : history.roots()) {
            all = all.and(planOver.apply(history.currentAssignmentRows(root.id())).withRoot(root.id().value()));
        }
        return all;
    }

    /** The rows of the post the command acts on; a hire or a reinstatement acts on the primary post, whose rows are all there is when it fills a gap. */
    private static List<VersionRow> postRows(PersonnelCommand command, PersonnelHistory history) {
        AssignmentId post = command.targetAssignmentId();
        if (post != null) {
            return history.currentAssignmentRows(post);
        }
        return history.primaryRoot().map(root -> history.currentAssignmentRows(root.id())).orElse(List.of());
    }

    /** The concurrent post a start made: the post of the rows that command wrote (the command itself names none). */
    private static Optional<AssignmentId> startedPost(PersonnelCommand command, PersonnelHistory history) {
        return history.currentAssignmentRows().stream().filter(row -> row.source().isCommand(command.id())).map(VersionRow::root).filter(Objects::nonNull)
                .map(AssignmentId::new).findFirst();
    }

    private static UUID postOf(PersonnelCommand command) {
        return Objects.requireNonNull(command.targetAssignmentId(), "post").value();
    }

    /** The columns the row's own command set: everything for a migrated row, what the command names for a command row. */
    private static Function<VersionRow, Set<String>> setters(PersonnelHistory history, VersionTable table) {
        Set<String> all = switch (table) {
            case EMPLOYEE -> CommandEffects.EMPLOYEE_COLUMNS;
            case ASSIGNMENT -> CommandEffects.ASSIGNMENT_COLUMNS;
            case LEGAL_ENTITY -> CommandEffects.LEGAL_ENTITY_COLUMNS;
        };
        return row -> {
            if (!row.source().command()) {
                return all;
            }
            PersonnelCommand c = history.lookup().get(row.source().commandId().value());
            return switch (table) {
                case EMPLOYEE -> CommandEffects.employeeValues(c).keySet();
                case ASSIGNMENT -> CommandEffects.assignmentValues(c).keySet();
                case LEGAL_ENTITY -> CommandEffects.legalEntityValues(c).keySet();
            };
        };
    }
}
