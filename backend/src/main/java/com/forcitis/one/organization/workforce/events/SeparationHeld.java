package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A separation was put on hold instead of taking effect (ADR-0068 item 2.2); the employee stays employed. effectiveDate is the separation's own, the envelope's businessDate. */
public record SeparationHeld(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate)
        implements DomainEvent {
}
