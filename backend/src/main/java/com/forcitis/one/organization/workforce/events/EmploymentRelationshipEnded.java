package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A separation took effect on one relationship (ADR-0086 design 6.1); effectiveDate is the day after the last working day, the first day the relationship is out of service. */
public record EmploymentRelationshipEnded(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
        LocalDate effectiveDate) implements DomainEvent {
}
