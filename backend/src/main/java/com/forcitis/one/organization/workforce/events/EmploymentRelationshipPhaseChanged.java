package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;

/** The employment phase of one relationship changed, by a regularization (ADR-0086 design 6.1); effectiveDate is the first day in the new phase. */
public record EmploymentRelationshipPhaseChanged(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, EmploymentRelationshipId employmentRelationshipId,
        LocalDate effectiveDate, EmploymentPhaseKind phaseKind) implements DomainEvent {
}
