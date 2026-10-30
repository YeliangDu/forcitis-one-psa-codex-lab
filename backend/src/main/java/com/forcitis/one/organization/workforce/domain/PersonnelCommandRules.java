package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The rules that span rows (ADR-0068 item 1.5), checked after the employee row is locked and before anything is written; the database holds only
 * the one-open-command-per-day index. A refused command leaves nothing behind because nothing was written yet.
 */
public final class PersonnelCommandRules {

    private PersonnelCommandRules() {
    }

    public static void check(PersonnelCommand command, PersonnelHistory history) {
        List<PersonnelCommand> others = history.commands();
        requireDayFree(command, others);
        requireSeparationOrder(command, others);
        requireStateOnTheDay(command, history.currentEmployeeRows());
    }

    private static void requireDayFree(PersonnelCommand command, List<PersonnelCommand> others) {
        if (command.kind() != PersonnelCommandKind.CORRECTION
                && others.stream().anyMatch(o -> o.status().isOpen() && o.effectiveDate().equals(command.effectiveDate()))) {
            throw refused("the employee already has an open command for " + command.effectiveDate());
        }
    }

    /** After a separation that no effective reinstatement covers, only a later reinstatement may be entered; a separation may not precede another command. */
    private static void requireSeparationOrder(PersonnelCommand command, List<PersonnelCommand> others) {
        Optional<PersonnelCommand> uncovered = others.stream().filter(o -> o.kind() == PersonnelCommandKind.SEPARATE)
                .filter(s -> others.stream().noneMatch(r -> r.kind() == PersonnelCommandKind.REINSTATE && r.status() == CommandStatus.EFFECTIVE
                        && r.effectiveDate().isAfter(s.effectiveDate())))
                .max(java.util.Comparator.comparing(PersonnelCommand::effectiveDate));
        if (command.kind() == PersonnelCommandKind.REINSTATE) {
            if (uncovered.isEmpty() || uncovered.get().status() != CommandStatus.EFFECTIVE || !command.effectiveDate().isAfter(uncovered.get().effectiveDate())) {
                throw refused("a reinstatement needs an effective separation before its effective date");
            }
            return;
        }
        if (uncovered.isPresent() && command.effectiveDate().isAfter(uncovered.get().effectiveDate())) {
            throw refused("the employee separates on " + uncovered.get().effectiveDate() + "; only a reinstatement can be dated later");
        }
        if (command.kind() == PersonnelCommandKind.SEPARATE && others.stream().anyMatch(o -> o.effectiveDate().isAfter(command.effectiveDate()))) {
            throw refused("a separation dated before another command of the employee is refused; correct those commands first");
        }
    }

    private static void requireStateOnTheDay(PersonnelCommand command, List<VersionRow> employeeRows) {
        LocalDate day = command.effectiveDate();
        VersionRow row = employeeRows.stream().filter(r -> r.covers(day)).findFirst().orElse(null);
        if (command.kind() == PersonnelCommandKind.HIRE) {
            if (!employeeRows.isEmpty()) {
                throw refused("the employee already has versions; a hire is the first");
            }
            return;
        }
        if (row == null) {
            throw refused("the employee has no version on " + day);
        }
        boolean employed = "EMPLOYED".equals(row.values().get(CommandEffects.EMPLOYMENT));
        if (command.kind() == PersonnelCommandKind.REINSTATE) {
            if (employed) {
                throw refused("the employee is employed on " + day);
            }
            return;
        }
        if (!employed) {
            throw refused("the employee is not employed on " + day);
        }
        Object phase = row.values().get(CommandEffects.PHASE);
        boolean onLeave = Boolean.TRUE.equals(row.values().get(CommandEffects.ON_LEAVE));
        switch (command.kind()) {
            case REGULARIZE -> {
                if (!"PROBATION".equals(phase)) {
                    throw refused("only an employee on probation can be regularized");
                }
            }
            case UNPAID_LEAVE_START -> {
                if (onLeave) {
                    throw refused("the employee is already on unpaid leave on " + day);
                }
            }
            case UNPAID_LEAVE_END -> {
                if (!onLeave) {
                    throw refused("the employee is not on unpaid leave on " + day);
                }
            }
            default -> {
            }
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, message);
    }
}
