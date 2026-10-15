package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

/** A person found in the organisation source at the first login was entered into the snapshot ahead of the next sync. */
public record EmployeeRegistered(EventEnvelope envelope, EmployeeId employeeId) implements DomainEvent {
}
