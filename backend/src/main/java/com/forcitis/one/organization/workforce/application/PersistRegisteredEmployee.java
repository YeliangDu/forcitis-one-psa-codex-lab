package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.RegisteredEmployee;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person and the event commit together; the audit entry is written from the event. */
@Service
class PersistRegisteredEmployee {
    private final OrgSnapshotRepository snapshots;
    private final DomainEvents events;
    private final Clock clock;

    PersistRegisteredEmployee(OrgSnapshotRepository snapshots, DomainEvents events, ObjectProvider<Clock> clock) {
        this.snapshots = snapshots;
        this.events = events;
        this.clock = Clocks.from(clock);
    }

    @Transactional
    public void execute(StaffEmployee staff) {
        RegisteredEmployee registered = RegisteredEmployee.onFirstLogin(staff, 0, snapshots.findEmployeeEventSequence(staff.userId()));
        snapshots.saveRegisteredEmployee(registered, clock.instant());
        events.publish(registered);
    }
}
