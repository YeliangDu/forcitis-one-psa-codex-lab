package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.CommandEffects;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.organization.workforce.domain.VersionTable;
import com.forcitis.one.organization.workforce.domain.VersionPlanner;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.util.Map;
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
            plans.put(VersionTable.ASSIGNMENT, VersionPlanner.endAt(history.currentAssignmentRows(), command.effectiveDate(), ids::next));
        } else {
            Map<String, Object> assignment = CommandEffects.assignmentValues(command);
            if (!assignment.isEmpty()) {
                plans.put(VersionTable.ASSIGNMENT, VersionPlanner.apply(history.currentAssignmentRows(), new VersionPlanner.Change(command.effectiveDate(),
                        assignment, row -> true, CommandEffects.coversEmptyDay(command)), source, setters(history, VersionTable.ASSIGNMENT), ids::next));
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
            plans.put(VersionTable.ASSIGNMENT, VersionPlanner.reopenAt(history.currentAssignmentRows(), command.effectiveDate(), ids::next));
        } else {
            Map<String, Object> assignment = CommandEffects.assignmentValues(command);
            if (!assignment.isEmpty()) {
                plans.put(VersionTable.ASSIGNMENT, VersionPlanner.revert(history.currentAssignmentRows(), id -> history.row(id), command.id(),
                        command.effectiveDate(), assignment.keySet(), row -> true, setters(history, VersionTable.ASSIGNMENT), ids::next));
            }
        }
        return plans;
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
