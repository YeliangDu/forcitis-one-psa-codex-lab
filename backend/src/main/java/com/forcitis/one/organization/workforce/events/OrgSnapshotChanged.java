package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;

/** Published when a sync run or a first login changes the snapshot; identity revokes sessions of separatedEmployees. */
public record OrgSnapshotChanged(EventEnvelope envelope, List<UserId> changedEmployees, List<UserId> separatedEmployees,
                                 List<OrgUnitId> changedOrgUnits) implements DomainEvent {
    public OrgSnapshotChanged {
        changedEmployees = List.copyOf(changedEmployees);
        separatedEmployees = List.copyOf(separatedEmployees);
        changedOrgUnits = List.copyOf(changedOrgUnits);
    }
}
