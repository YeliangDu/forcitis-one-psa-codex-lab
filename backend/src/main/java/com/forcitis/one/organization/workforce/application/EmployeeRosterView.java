package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record EmployeeRosterView(UUID employeeId, String employeeCode, String displayName, String employmentStatus,
                                 @Nullable LocalDate hireDate, boolean onUnpaidLeave, List<AssignmentView> assignments) {
    static EmployeeRosterView of(EmployeeRosterQueries.Row row) {
        return new EmployeeRosterView(row.employeeId().value(), row.employeeCode(), row.displayName(), row.employmentStatus().name(), row.hireDate(), row.onUnpaidLeave(),
                row.assignments().stream().map(a -> new AssignmentView(a.assignmentKind().name(), a.orgUnitId().value(), a.orgUnitName(),
                        a.positionId() == null ? null : a.positionId().value(), a.positionName(),
                        a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value(), a.reportsToDisplayName())).toList());
    }

    public record AssignmentView(String assignmentKind, UUID orgUnitId, String orgUnitName, @Nullable UUID positionId,
                                 @Nullable String positionName, @Nullable UUID reportsToEmployeeId, @Nullable String reportsToDisplayName) {
    }
}
