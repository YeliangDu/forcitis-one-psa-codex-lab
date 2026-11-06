package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Entry rules for dated coverage, independent of permissions and approval routing (#1566). */
public final class ActingDutyRules {
    private ActingDutyRules() {
    }

    public static boolean isActing(PersonnelCommandKind kind) {
        return kind == PersonnelCommandKind.ACTING_START || kind == PersonnelCommandKind.ACTING_END || kind == PersonnelCommandKind.ACTING_EXTEND;
    }

    public static void requireStart(PersonnelCommand command) {
        if (command.actingCoveredOrgUnitId() == null || command.actingEndsBeforeDate() == null
                || !command.actingEndsBeforeDate().isAfter(command.effectiveDate()) || command.targetActingDutyId() != null) {
            throw refused("an acting start needs a covered department and an exclusive end after its effective date, and names no existing duty");
        }
        String reason = command.reason();
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > PersonnelCommand.MAX_REASON_LENGTH || reason.indexOf('\0') >= 0) {
            throw refused("an acting start needs a nonblank reason of 1 to 500 characters without NUL");
        }
        if (command.employeeId().equals(command.actingCoveredEmployeeId())) {
            throw refused("an employee cannot cover themself");
        }
    }

    /** Checks the person's whole history, including posts and pending starts belonging to other relationships. */
    public static void requireAvailable(PersonnelCommand command, PersonnelHistory history, List<ActingDuty> duties) {
        LocalDate from = command.effectiveDate();
        LocalDate to = Objects.requireNonNull(command.actingEndsBeforeDate());
        boolean occupied = duties.stream().anyMatch(d -> d.employeeId().equals(command.employeeId()) && d.endedOn() == null
                && Objects.equals(d.coveredOrgUnitId(), command.actingCoveredOrgUnitId())
                && Objects.equals(d.coveredPositionId(), command.actingCoveredPositionId()) && overlaps(from, to, d.startsOn(), d.endsBefore()));
        boolean reserved = history.commands().stream().anyMatch(c -> !c.id().equals(command.id()) && c.employeeId().equals(command.employeeId())
                && c.kind() == PersonnelCommandKind.ACTING_START && c.status() == CommandStatus.PENDING
                && Objects.equals(c.actingCoveredOrgUnitId(), command.actingCoveredOrgUnitId())
                && Objects.equals(c.actingCoveredPositionId(), command.actingCoveredPositionId())
                && overlaps(from, to, c.effectiveDate(), c.actingEndsBeforeDate()));
        if (occupied || reserved) {
            throw refused("the employee already covers this post during the acting term");
        }
        Object unit = Objects.requireNonNull(command.actingCoveredOrgUnitId()).value();
        Object position = command.actingCoveredPositionId() == null ? null : command.actingCoveredPositionId().value();
        boolean own = history.currentAssignmentRows().stream().anyMatch(r -> unit.equals(r.values().get(CommandEffects.ORG_UNIT))
                && Objects.equals(position, r.values().get(CommandEffects.POSITION)) && overlaps(from, to, r.from(), r.to()));
        if (own) {
            throw refused("the covered post is one of the employee's own posts during the acting term");
        }
    }

    private static boolean overlaps(LocalDate from, LocalDate to, LocalDate otherFrom, @Nullable LocalDate otherTo) {
        return otherFrom.isBefore(to) && (otherTo == null || from.isBefore(otherTo));
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, message);
    }
}
