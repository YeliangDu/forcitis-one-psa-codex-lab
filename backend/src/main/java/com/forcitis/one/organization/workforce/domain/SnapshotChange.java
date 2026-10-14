package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;

/** Who and what a sync run changed, as ids; already-separated and already-revoked rows are never repeated. */
public record SnapshotChange(List<UserId> changedEmployees, List<UserId> separatedEmployees, List<OrgUnitId> changedOrgUnits) {
    public SnapshotChange {
        changedEmployees = List.copyOf(changedEmployees);
        separatedEmployees = List.copyOf(separatedEmployees);
        changedOrgUnits = List.copyOf(changedOrgUnits);
    }

    public boolean hasContent() {
        return !changedEmployees.isEmpty() || !separatedEmployees.isEmpty() || !changedOrgUnits.isEmpty();
    }
}
