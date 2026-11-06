package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A dated coverage fact within the employee aggregate; an ended row retains its original term. */
public record ActingDuty(ActingDutyId id, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
                         OrgUnitId coveredOrgUnitId, @Nullable PositionId coveredPositionId, @Nullable EmployeeId coveredEmployeeId,
                         LocalDate startsOn, LocalDate endsBefore, @Nullable LocalDate endedOn,
                         String reason, PersonnelCommandId sourcePersonnelCommandId, UserId createdBy, Instant createdAt, long version) {
    /** A backdated separation cannot end coverage before it starts; a same-day separation closes it on that day. */
    public LocalDate separationEndOn(LocalDate separationDay) {
        return startsOn.isAfter(separationDay) ? startsOn : separationDay;
    }

    public static ActingDuty start(ActingDutyId id, PersonnelCommand command, Instant at) {
        return new ActingDuty(id, command.employeeId(), Objects.requireNonNull(command.employmentRelationshipId()),
                Objects.requireNonNull(command.actingCoveredOrgUnitId()), command.actingCoveredPositionId(), command.actingCoveredEmployeeId(),
                command.effectiveDate(), Objects.requireNonNull(command.actingEndsBeforeDate()), null, Objects.requireNonNull(command.reason()),
                command.id(), command.createdBy(), at, 0);
    }
}
