package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

public record Assignment(EmployeeId employeeId, OrgUnitId orgUnitId, PositionId positionId, EmployeeId reportsToEmployeeId) {
    public Assignment {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }
}
