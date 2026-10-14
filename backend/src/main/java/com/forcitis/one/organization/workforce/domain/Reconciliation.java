package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;

/** Already-separated or already-revoked rows are never repeated in the event lists. */
public record Reconciliation(List<Position> positions, List<OrgUnit> orgUnits, List<StaffMember> staff,
                             List<SyncDiff> diffs, List<UserId> changedEmployees, List<UserId> separatedEmployees,
                             List<OrgUnitId> changedOrgUnits, RunStats stats) {
    public Reconciliation {
        positions = List.copyOf(positions);
        orgUnits = List.copyOf(orgUnits);
        staff = List.copyOf(staff);
        diffs = List.copyOf(diffs);
        changedEmployees = List.copyOf(changedEmployees);
        separatedEmployees = List.copyOf(separatedEmployees);
        changedOrgUnits = List.copyOf(changedOrgUnits);
    }

    public boolean hasChanges() {
        return !positions.isEmpty() || !orgUnits.isEmpty() || !staff.isEmpty();
    }

    public boolean hasEventContent() {
        return !changedEmployees.isEmpty() || !separatedEmployees.isEmpty() || !changedOrgUnits.isEmpty();
    }
}
