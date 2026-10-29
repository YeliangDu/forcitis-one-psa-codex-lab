package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record Assignment(EmployeeId employeeId, OrgUnitId orgUnitId, @Nullable PositionId positionId, @Nullable EmployeeId reportsToEmployeeId) {
    public Assignment {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }
}
