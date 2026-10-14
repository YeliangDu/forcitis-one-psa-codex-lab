package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the one person found by the login-miss lookup, audits it and announces the change in the same transaction.
 * The audit entry joins the transaction: if the audit cannot be written, the person is not created.
 */
@Service
class PersistRegisteredEmployee {

    private final OrgSnapshotRepository snapshots;
    private final ObjectProvider<AuditLog> audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PersistRegisteredEmployee(OrgSnapshotRepository snapshots, ObjectProvider<AuditLog> audit,
                              ApplicationEventPublisher events, ObjectProvider<Clock> clock) {
        this.snapshots = snapshots;
        this.audit = audit;
        this.events = events;
        this.clock = Clocks.from(clock);
    }

    @Transactional
    public void execute(StaffMember member) {
        snapshots.saveStaffMember(member, null, clock.instant());
        audit.getObject().record(new AuditEntry("organization.workforce.employee.registered-on-login", null, null,
                "employee:" + member.userId().value(),
                "first login before the next sync: person found in the organisation source and registered"));
        events.publishEvent(new OrgSnapshotChanged(List.of(member.userId()), List.of(), List.of()));
    }
}
