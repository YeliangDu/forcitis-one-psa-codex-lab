package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.EmployeeRosterView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record EmployeeDetailResponse(UUID employeeId, String employeeCode, String displayName, String employmentStatus, boolean onUnpaidLeave,
                              @Nullable LocalDate hireDate, List<DetailAssignmentResponse> assignments) {
    static EmployeeDetailResponse of(EmployeeRosterView view) {
        return new EmployeeDetailResponse(view.employeeId(), view.employeeCode(), view.displayName(), view.employmentStatus(), view.onUnpaidLeave(), view.hireDate(),
                view.assignments().stream().map(a -> new DetailAssignmentResponse(a.assignmentKind(), a.orgUnitId(), a.orgUnitName(), a.positionId(), a.positionName(),
                        a.reportsToEmployeeId(), a.reportsToDisplayName())).toList());
    }

    record DetailAssignmentResponse(String assignmentKind, UUID orgUnitId, String orgUnitName, @Nullable UUID positionId, @Nullable String positionName,
                              @Nullable UUID reportsToEmployeeId, @Nullable String reportsToDisplayName) {
    }
}
