package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/** Where an employee works and who they report to. reportsToEmployeeId is null when A8 has no (valid) direct leader. */
public record Assignment(UserId employeeId, OrgUnitId orgUnitId, PositionId positionId, UserId reportsToEmployeeId) {

    public Assignment {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }
}
