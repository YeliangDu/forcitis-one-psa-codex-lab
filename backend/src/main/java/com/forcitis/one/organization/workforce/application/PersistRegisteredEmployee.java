package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.RegisteredEmployee;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The person and the event commit together, in a transaction of their own: a login that registers a new hire does not hold the snapshot writer
 * lock until it commits and does not change the account aggregate in the same transaction (the account is opened from EmployeeRegistered, or by
 * the login itself, idempotently). The audit entry is written from the event. Takes the snapshot writer lock, so two registrations never
 * write at once, and the second of two first logins finds the person already registered and writes nothing.
 */
@Service
class PersistRegisteredEmployee {
    private final OrgSnapshotRepository snapshots;
    private final OrgSnapshotQueries lookups;
    private final DomainEvents events;
    private final Clock clock;

    PersistRegisteredEmployee(OrgSnapshotRepository snapshots, OrgSnapshotQueries lookups, DomainEvents events, Clock clock) {
        this.snapshots = snapshots;
        this.lookups = lookups;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(StaffEmployee staff) {
        snapshots.lockForWrite();
        if (lookups.employeeExists(staff.employeeId())) {
            return;
        }
        RegisteredEmployee registered = RegisteredEmployee.onFirstLogin(staff, 0, snapshots.findEmployeeEventSequence(staff.employeeId()));
        snapshots.saveRegisteredEmployee(registered, clock.instant());
        events.publish(registered);
    }
}
