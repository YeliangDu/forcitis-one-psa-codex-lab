package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The rules that span rows (ADR-0068 item 1.5), checked after the employee row is locked and before anything is written; the database holds only
 * the one-open-command-per-day index. A refused command leaves nothing behind because nothing was written yet.
 */
public final class PersonnelCommandRules {

    private PersonnelCommandRules() {
    }

    /** The command may be the one stored already (an awaiting command judged again at its last countersign): it is not its own other. */
    public static void check(PersonnelCommand command, PersonnelHistory history) {
        List<PersonnelCommand> others = history.commands().stream().filter(o -> !o.id().equals(command.id())).toList();
        requireDayFree(command, others);
        requireSeparationOrder(command, others);
        requireStateOnTheDay(command, history.currentEmployeeRows());
        requireLiveConcurrentPost(command, history);
        requireCorrectionOpenEnded(command, history);
        requirePostsEndWithTheSeparation(command, others);
    }

    /**
     * A separation ends every post at its day and its withdrawal reopens what ends on that day, so a post is neither started past a written separation nor ended on
     * the day of one (#711): the end of the post would come back with the separation's withdrawal, and the start would stay open after it.
     */
    private static void requirePostsEndWithTheSeparation(PersonnelCommand command, List<PersonnelCommand> others) {
        if (command.kind() == PersonnelCommandKind.START_CONCURRENT && others.stream().anyMatch(o -> o.kind() == PersonnelCommandKind.SEPARATE && writesVersions(o)
                && o.effectiveDate().isAfter(command.effectiveDate()))) {
            throw refused("the employee separates after " + command.effectiveDate() + "; a concurrent post is started after the separation is withdrawn, or not at all");
        }
        PersonnelCommandKind clash = switch (command.kind()) {
            case SEPARATE -> PersonnelCommandKind.END_CONCURRENT;
            case END_CONCURRENT -> PersonnelCommandKind.SEPARATE;
            default -> null;
        };
        if (clash != null && others.stream().anyMatch(o -> o.kind() == clash && o.status() != CommandStatus.WITHDRAWN && o.effectiveDate().equals(command.effectiveDate()))) {
            throw refused("a separation and the end of a concurrent post may not share the day " + command.effectiveDate());
        }
    }

    private static boolean writesVersions(PersonnelCommand command) {
        return command.status() == CommandStatus.PENDING || command.status() == CommandStatus.EFFECTIVE;
    }

    /** The end of a concurrent post is taken back only while no separation written on or after its day would have ended the post anyway (#711). */
    public static void requireNoSeparationAtOrAfter(PersonnelCommand command, PersonnelHistory history) {
        if (command.kind() == PersonnelCommandKind.END_CONCURRENT && history.commands().stream().anyMatch(o -> o.kind() == PersonnelCommandKind.SEPARATE
                && writesVersions(o) && !o.effectiveDate().isBefore(command.effectiveDate()))) {
            throw refused("the employee separates on or after " + command.effectiveDate() + "; withdraw the separation first");
        }
    }

    /**
     * A correction runs from its day to the end of time (W2 owner ruling on #872): what it carries must be the same in every current version from that
     * day on. A later version, from an effective or a pending command, that changed the attribute is first dealt with by its own correction.
     */
    private static void requireCorrectionOpenEnded(PersonnelCommand command, PersonnelHistory history) {
        if (command.kind() != PersonnelCommandKind.CORRECTION) {
            return;
        }
        List<VersionRow> post = command.targetAssignmentId() == null ? List.of() : history.currentAssignmentRows(command.targetAssignmentId());
        requireConstant(CommandEffects.assignmentValues(command).keySet(), post, command.effectiveDate(), row -> true);
        requireConstant(CommandEffects.legalEntityValues(command).keySet(), history.currentLegalEntityRows(), command.effectiveDate(), row -> true);
        requireConstant(CommandEffects.employeeValues(command).keySet(), history.currentEmployeeRows(), command.effectiveDate(), CommandEffects.employeeFilter(command));
    }

    private static void requireConstant(java.util.Set<String> columns, List<VersionRow> rows, LocalDate from, java.util.function.Predicate<VersionRow> filter) {
        List<VersionRow> affected = rows.stream().filter(r -> r.endsAfter(from)).filter(filter).toList();
        for (String column : columns) {
            if (affected.stream().map(r -> r.values().get(column)).distinct().count() > 1) {
                throw refused("a later version changes " + column + " after " + from + "; correct those commands first");
            }
        }
    }

    /** One open command a day for the person and the primary post together, one for each concurrent post; a start of a concurrent post is not counted (#711). */
    private static void requireDayFree(PersonnelCommand command, List<PersonnelCommand> others) {
        Object bucket = dayBucket(command);
        if (command.kind() != PersonnelCommandKind.CORRECTION && bucket != null
                && others.stream().anyMatch(o -> o.status().isOpen() && o.effectiveDate().equals(command.effectiveDate()) && bucket.equals(dayBucket(o)))) {
            throw refused("the employee already has an open command for " + command.effectiveDate());
        }
    }

    private static final Object PERSON = new Object();

    private static @Nullable Object dayBucket(PersonnelCommand command) {
        if (command.kind() == PersonnelCommandKind.START_CONCURRENT) {
            return null;
        }
        return command.targetAssignmentKind() == AssignmentKind.CONCURRENT ? command.targetAssignmentId() : PERSON;
    }

    /** A command on a concurrent post needs a version of that post in force on its day: a post that has ended, or never started, takes no command (#711). */
    private static void requireLiveConcurrentPost(PersonnelCommand command, PersonnelHistory history) {
        if (command.targetAssignmentKind() != AssignmentKind.CONCURRENT) {
            return;
        }
        if (history.currentAssignmentRows(Objects.requireNonNull(command.targetAssignmentId())).stream().noneMatch(row -> row.covers(command.effectiveDate()))) {
            throw refused("the concurrent post has no version on " + command.effectiveDate());
        }
    }

    /**
     * While an employee has a command awaiting countersign, no command dated on or after it is entered (user answer Q4 of 2026-10-30): its versions are not written,
     * so a later command would be judged without it and could switch the cost legal entity back when the awaiting one is written. Earlier ones are judged at their own approval.
     * Checked at entry only; the approval of the awaiting command re-judges the basis instead.
     */
    public static void requireNoAwaitingOnOrBefore(PersonnelCommand command, PersonnelHistory history) {
        if (history.commands().stream().anyMatch(o -> o.status() == CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN && !o.effectiveDate().isAfter(command.effectiveDate()))) {
            throw refused("the employee has a command awaiting the legal entity countersign on or before " + command.effectiveDate() + "; wait for it or withdraw it first");
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
        if (command.kind() == PersonnelCommandKind.SEPARATE && others.stream().anyMatch(o -> o.status() != CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN && o.effectiveDate().isAfter(command.effectiveDate()))) {
            throw refused("a separation dated before another command of the employee is refused; correct those commands first (a command still awaiting countersign has written nothing; its approval judges it again)");
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

    /**
     * An assignment row a command writes carries a position (user decision 2026-10-30; ck_assignment_versions_command_position). A transfer by department
     * only keeps the position of the row it follows, and taking a command back restores earlier values over later rows; where that would leave a row a
     * command wrote without a position, the call is refused, and since the whole call is one transaction nothing of it stays.
     */
    public static void requirePositionOnCommandRows(@Nullable VersionPlan assignments) {
        if (assignments != null && assignments.inserts().stream().anyMatch(row -> row.source().command() && row.values().get(CommandEffects.POSITION) == null)) {
            throw refused("an assignment row written by a command needs a position; name one on the command, or withdraw the later command that relies on it first");
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, message);
    }
}
