package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;

/** Published after a sync run commits; identity revokes sessions of separatedEmployees. */
public record OrgSnapshotChanged(List<UserId> changedEmployees, List<UserId> separatedEmployees,
                                 List<OrgUnitId> changedOrgUnits) {
    public OrgSnapshotChanged {
        changedEmployees = List.copyOf(changedEmployees);
        separatedEmployees = List.copyOf(separatedEmployees);
        changedOrgUnits = List.copyOf(changedOrgUnits);
    }
}
