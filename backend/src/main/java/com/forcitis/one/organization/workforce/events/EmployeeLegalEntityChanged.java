package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The employee's cost legal entity changed (ADR-0073 item 3.7): a command that needed the finance countersign took effect: a personnel command, or an org-structure command that changed the employee's cost legal entity (exactly one of the two ids is set). effectiveDate is the first day in
 * the new legal entity and the envelope's businessDate; the envelope's legalEntityId is the new one.
 */
public record EmployeeLegalEntityChanged(EventEnvelope envelope, @Nullable PersonnelCommandId personnelCommandId, @Nullable OrgStructureCommandId orgStructureCommandId,
                                         EmployeeId employeeId, LocalDate effectiveDate, LegalEntityId previousLegalEntityId, LegalEntityId newLegalEntityId) implements DomainEvent {
}
