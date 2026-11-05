package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** The employee moved from probation to regular employment (ADR-0068 item 1.3); effectiveDate is the first regular day and the envelope's businessDate. */
public record EmployeeRegularized(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
        @Nullable EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {

    /** For callers that build the event without a relationship (the relationship id is then absent). */
    public EmployeeRegularized(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate) {
        this(envelope, personnelCommandId, employeeId, effectiveDate, null);
    }
}
