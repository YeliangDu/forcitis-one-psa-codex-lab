package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** An unpaid-leave start took effect (ADR-0068 items 1.9 and 3); effectiveDate is the first day on leave and the envelope's businessDate. */
public record EmployeeUnpaidLeaveStarted(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
        @Nullable EmploymentRelationshipId employmentRelationshipId) implements DomainEvent {

    /** For callers that build the event without a relationship (the relationship id is then absent). */
    public EmployeeUnpaidLeaveStarted(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate) {
        this(envelope, personnelCommandId, employeeId, effectiveDate, null);
    }
}
