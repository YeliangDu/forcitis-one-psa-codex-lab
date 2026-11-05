package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A separation took effect (ADR-0068 item 1.9); effectiveDate is the day after the last working day HR entered, the first day separated,
 * and the envelope's businessDate. A held separation publishes this only after its release.
 */
public record EmployeeSeparated(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
        EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {
}
