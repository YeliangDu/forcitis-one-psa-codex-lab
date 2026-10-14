package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.LocalDate;
import java.util.List;

/**
 * Published when a sync run changes the snapshot; identity revokes sessions of separatedEmployees. The envelope's businessDate is the
 * snapshot cut-off day; effectiveFrom is the day the changes take effect, which the source does not state, so it is that same day.
 */
public record OrgSnapshotChanged(EventEnvelope envelope, List<UserId> changedEmployees, List<UserId> separatedEmployees,
                                 List<OrgUnitId> changedOrgUnits, LocalDate effectiveFrom) implements DomainEvent {
    public OrgSnapshotChanged {
        changedEmployees = List.copyOf(changedEmployees);
        separatedEmployees = List.copyOf(separatedEmployees);
        changedOrgUnits = List.copyOf(changedOrgUnits);
    }
}
