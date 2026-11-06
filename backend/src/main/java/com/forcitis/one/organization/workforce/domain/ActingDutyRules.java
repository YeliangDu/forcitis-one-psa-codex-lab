package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
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
        requireReason(command.reason());
        if (command.employeeId().equals(command.actingCoveredEmployeeId())) {
            throw refused("an employee cannot cover themself");
        }
    }

    public static void requireChange(PersonnelCommand command) {
        if (command.targetActingDutyId() == null || command.actingCoveredOrgUnitId() != null
                || command.actingCoveredPositionId() != null || command.actingCoveredEmployeeId() != null
                || (command.kind() == PersonnelCommandKind.ACTING_EXTEND) != (command.actingEndsBeforeDate() != null)) {
            throw refused("an acting change names an existing duty, no covered fields, and an exclusive end only for an extension");
        }
        requireReason(command.reason());
    }

    private static void requireReason(@Nullable String reason) {
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > PersonnelCommand.MAX_REASON_LENGTH || reason.indexOf('\0') >= 0) {
            throw refused("an acting command needs a nonblank reason of 1 to 500 characters without NUL");
        }
    }

    public static ActingDuty requireTarget(PersonnelCommand command, @Nullable ActingDuty duty) {
        if (duty == null || !duty.id().equals(command.targetActingDutyId()) || !duty.employeeId().equals(command.employeeId())
                || !duty.employmentRelationshipId().equals(command.employmentRelationshipId()) || duty.endedOn() != null) {
            throw refused("the target acting duty must be open and belong to the same employee and employment relationship");
        }
        return duty;
    }

    public static void requireEnd(PersonnelCommand command, ActingDuty duty, List<PersonnelCommand> others) {
        requireTarget(command, duty);
        if (!command.effectiveDate().isAfter(duty.startsOn()) || !command.effectiveDate().isBefore(duty.endsBefore())) {
            throw refused("an early end must be after the duty starts and before its exclusive end");
        }
        if (others.stream().anyMatch(c -> !c.id().equals(command.id()) && c.kind() == PersonnelCommandKind.ACTING_END
                && c.status() == CommandStatus.PENDING && duty.id().equals(c.targetActingDutyId()))) {
            throw refused("the acting duty already has a pending early end");
        }
    }

    /** True when a separation that no effective reinstatement covers falls before the start day, so the start can never take effect. */
    public static boolean startIsAfterSeparation(PersonnelCommand start, List<PersonnelCommand> all) {
        return all.stream().filter(s -> s.kind() == PersonnelCommandKind.SEPARATE && s.status() != CommandStatus.WITHDRAWN
                        && s.status() != CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN && s.effectiveDate().isBefore(start.effectiveDate()))
                .anyMatch(s -> all.stream().noneMatch(r -> r.kind() == PersonnelCommandKind.REINSTATE && r.status() == CommandStatus.EFFECTIVE
                        && r.effectiveDate().isAfter(s.effectiveDate()) && !r.effectiveDate().isAfter(start.effectiveDate())));
    }

    public static void requireExtend(PersonnelCommand command, ActingDuty duty, PersonnelHistory history, List<ActingDuty> duties) {
        requireTarget(command, duty);
        LocalDate to = Objects.requireNonNull(command.actingEndsBeforeDate());
        if (!to.isAfter(duty.endsBefore())) {
            throw refused("an extension must grow the duty's exclusive end");
        }
        requireAvailable(command, history, duties, duty.coveredOrgUnitId(), duty.coveredPositionId(), duty.startsOn(), to, duty.id());
    }

    /** Checks the person's whole history, including posts and pending starts belonging to other relationships. */
    public static void requireAvailable(PersonnelCommand command, PersonnelHistory history, List<ActingDuty> duties) {
        requireAvailable(command, history, duties, Objects.requireNonNull(command.actingCoveredOrgUnitId()), command.actingCoveredPositionId(),
                command.effectiveDate(), Objects.requireNonNull(command.actingEndsBeforeDate()), null);
    }

    private static void requireAvailable(PersonnelCommand command, PersonnelHistory history, List<ActingDuty> duties,
                                         OrgUnitId unit, @Nullable PositionId position, LocalDate from, LocalDate to, @Nullable ActingDutyId except) {
        boolean occupied = duties.stream().anyMatch(d -> !d.id().equals(except) && d.employeeId().equals(command.employeeId())
                && d.coveredOrgUnitId().equals(unit) && Objects.equals(d.coveredPositionId(), position)
                && overlaps(from, to, d.startsOn(), d.endedOn() == null ? d.endsBefore() : d.endedOn()));
        boolean reserved = history.commands().stream().anyMatch(c -> !c.id().equals(command.id()) && c.employeeId().equals(command.employeeId())
                && c.kind() == PersonnelCommandKind.ACTING_START && c.status() == CommandStatus.PENDING
                && Objects.equals(c.actingCoveredOrgUnitId(), unit) && Objects.equals(c.actingCoveredPositionId(), position)
                && overlaps(from, to, c.effectiveDate(), c.actingEndsBeforeDate()));
        if (occupied || reserved) {
            throw refused("the employee already covers this post during the acting term");
        }
        Object post = position == null ? null : position.value();
        boolean own = history.currentAssignmentRows().stream().anyMatch(r -> unit.equals(new OrgUnitId((java.util.UUID) r.values().get(CommandEffects.ORG_UNIT)))
                && Objects.equals(post, r.values().get(CommandEffects.POSITION)) && overlaps(from, to, r.from(), r.to()));
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
