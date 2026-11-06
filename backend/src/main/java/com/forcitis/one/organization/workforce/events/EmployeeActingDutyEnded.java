package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * An acting duty ended, once per duty: endedOn is the envelope's businessDate. personnelCommandId is the early-end or the separation command and is empty
 * when the duty expired (the due job closes it with no command). Ids, dates and the cause only.
 */
public record EmployeeActingDutyEnded(EventEnvelope envelope, @Nullable PersonnelCommandId personnelCommandId, EmployeeId employeeId, ActingDutyId actingDutyId,
                                      OrgUnitId coveredOrgUnitId, @Nullable PositionId coveredPositionId, @Nullable EmployeeId coveredEmployeeId,
                                      LocalDate endedOn, ActingDutyEndCause endCause, EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {
}
