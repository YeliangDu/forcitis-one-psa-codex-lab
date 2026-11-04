package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** A bean of its own, so that a test double of OrgDirectory does not take it away. */
@Component
class EmployeeLabelsService implements EmployeeLabels {
    private final OrgSnapshotQueries snapshots;

    EmployeeLabelsService(OrgSnapshotQueries snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public Map<EmployeeId, EmployeeLabel> labelsOf(Set<EmployeeId> employeeIds) {
        return snapshots.findLabels(employeeIds);
    }
}
