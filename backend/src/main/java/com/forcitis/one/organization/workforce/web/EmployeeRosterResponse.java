package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.EmployeeRosterView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record EmployeeRosterResponse(UUID employeeId, String employeeCode, String displayName, String employmentStatus, boolean onUnpaidLeave,
                              @Nullable LocalDate hireDate, List<RosterAssignmentResponse> assignments) {
    static EmployeeRosterResponse of(EmployeeRosterView view) {
        return new EmployeeRosterResponse(view.employeeId(), view.employeeCode(), view.displayName(), view.employmentStatus(), view.onUnpaidLeave(), view.hireDate(),
                view.assignments().stream().map(a -> new RosterAssignmentResponse(a.assignmentKind(), a.orgUnitId(), a.orgUnitName(), a.positionId(), a.positionName())).toList());
    }

    record RosterAssignmentResponse(String assignmentKind, UUID orgUnitId, String orgUnitName, @Nullable UUID positionId, @Nullable String positionName) {
    }
}
