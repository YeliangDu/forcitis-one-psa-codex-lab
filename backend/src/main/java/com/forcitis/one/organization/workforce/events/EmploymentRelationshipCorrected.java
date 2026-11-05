package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A correction changed the employee-level attributes of one relationship (ADR-0086 design 6.1); effectiveDate is the day the correction runs from. */
public record EmploymentRelationshipCorrected(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
        LocalDate effectiveDate) implements DomainEvent {
}
