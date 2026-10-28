package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A reinstatement after an effective separation took effect (ADR-0068 items 1.5, 1.10); effectiveDate is the first day employed again and the envelope's businessDate. */
public record EmployeeReinstated(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate)
        implements DomainEvent {
}
