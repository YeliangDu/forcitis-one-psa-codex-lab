package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import java.util.List;
import java.util.Objects;

/** A person found in the organisation source at the first login, before the next sync would have brought them in. */
public final class RegisteredEmployee extends AggregateRoot {
    private final StaffEmployee staff;

    private RegisteredEmployee(StaffEmployee staff, long version, long eventSequence) {
        super(version, eventSequence);
        this.staff = Objects.requireNonNull(staff, "staff");
    }

    public static RegisteredEmployee onFirstLogin(StaffEmployee staff, long version, long eventSequence) {
        RegisteredEmployee registered = new RegisteredEmployee(staff, version, eventSequence);
        registered.record((envelopes, sequence) -> new EmployeeRegistered(envelopes.wrap(new EventDraft(
                "organization.workforce.EmployeeRegistered", 1, "Employee", String.valueOf(staff.employeeId().value()), sequence, null, null,
                new EventSource(SourceSystem.ONE, null), List.of())), staff.employeeId()));
        return registered;
    }

    public StaffEmployee staff() {
        return staff;
    }
}
