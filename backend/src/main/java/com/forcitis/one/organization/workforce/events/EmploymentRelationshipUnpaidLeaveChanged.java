package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** One relationship started or ended an unpaid leave (ADR-0086 design 6.1); effectiveDate is the first day of the new state. */
public record EmploymentRelationshipUnpaidLeaveChanged(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
        LocalDate effectiveDate, boolean onUnpaidLeave) implements DomainEvent {
}
