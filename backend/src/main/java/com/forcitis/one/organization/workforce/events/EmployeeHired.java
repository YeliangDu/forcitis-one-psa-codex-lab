package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A hire took effect (ADR-0068 item 1.9); effectiveDate is the personnel effective date, the first day employed, and the envelope's businessDate. */
public record EmployeeHired(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
        EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {
}
