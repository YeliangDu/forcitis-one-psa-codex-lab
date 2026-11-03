package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/**
 * The employee's cost legal entity changed (ADR-0073 item 3.7): a command that needed the finance countersign took effect. effectiveDate is the first day in
 * the new legal entity and the envelope's businessDate; the envelope's legalEntityId is the new one.
 */
public record EmployeeLegalEntityChanged(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
                                         LegalEntityId previousLegalEntityId, LegalEntityId newLegalEntityId) implements DomainEvent {
}
