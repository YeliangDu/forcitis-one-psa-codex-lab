package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An acting duty took effect: the employee stands in for a unit (the unit head when no post is named) or a post from startsOn, the envelope's businessDate,
 * until endsBefore (the value at the start; later extensions publish nothing). Ids and dates only (the signed workforce-acting-duty data design record).
 */
public record EmployeeActingDutyStarted(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, ActingDutyId actingDutyId,
                                        OrgUnitId coveredOrgUnitId, @Nullable PositionId coveredPositionId, @Nullable EmployeeId coveredEmployeeId,
                                        LocalDate startsOn, LocalDate endsBefore, EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {
}
