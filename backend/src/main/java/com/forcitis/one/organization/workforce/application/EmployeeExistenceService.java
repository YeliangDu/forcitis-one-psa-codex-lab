package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeExistence;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import org.springframework.stereotype.Component;

/** A bean of its own, not a second interface of OrgDirectoryService, so that a test double of OrgDirectory does not take it away. */
@Component
class EmployeeExistenceService implements EmployeeExistence {
    private final OrgSnapshotQueries snapshots;

    EmployeeExistenceService(OrgSnapshotQueries snapshots) {
        this.snapshots = snapshots;
    }

    @Override
    public boolean exists(EmployeeId employeeId) {
        return snapshots.employeeExists(employeeId);
    }
}
