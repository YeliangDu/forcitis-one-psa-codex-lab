package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;

/**
 * What one run would change: only the changed rows (positions, orgUnits, staff), the report rows (diffs, including
 * data exceptions), the figures for the shrink guard and the content of the org-change event. The event lists come
 * from the same synced-field list as the diffs (RULE-373); already-separated or already-revoked rows are never
 * repeated.
 */
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

    /** True when the org-change event has something to say. */
    public boolean hasEventContent() {
        return !changedEmployees.isEmpty() || !separatedEmployees.isEmpty() || !changedOrgUnits.isEmpty();
    }
}
