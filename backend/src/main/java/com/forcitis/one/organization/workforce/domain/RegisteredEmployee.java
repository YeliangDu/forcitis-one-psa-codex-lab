package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import java.util.List;
import java.util.Objects;

/** A person found in the organisation source at the first login, before the next sync would have brought them in. */
public final class RegisteredEmployee extends AggregateRoot {
    private final StaffEmployee staff;

    private RegisteredEmployee(StaffEmployee staff) {
        super(0);
        this.staff = Objects.requireNonNull(staff, "staff");
    }

    public static RegisteredEmployee onFirstLogin(StaffEmployee staff) {
        RegisteredEmployee registered = new RegisteredEmployee(staff);
        registered.record((envelopes, version) -> new OrgSnapshotChanged(envelopes.wrap(new EventDraft(
                "organization.workforce.OrgSnapshotChanged", 1, "Employee", String.valueOf(staff.userId().value()), version, null, null,
                new EventSource(SourceSystem.ONE, null), List.of())), List.of(staff.userId()), List.of(), List.of()));
        return registered;
    }

    public StaffEmployee staff() {
        return staff;
    }
}
