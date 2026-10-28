package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** A held separation was released (ADR-0068 item 2.3) and is carried out at its original effectiveDate, which is the envelope's businessDate. */
public record SeparationHoldReleased(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate)
        implements DomainEvent {
}
