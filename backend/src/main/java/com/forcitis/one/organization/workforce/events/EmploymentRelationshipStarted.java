package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A relationship came into service: a hire made it, or a reinstatement brought it back (ADR-0086 design 6.1); effectiveDate is its first day in service and the envelope's businessDate. */
public record EmploymentRelationshipStarted(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
        LocalDate effectiveDate) implements DomainEvent {
}
