package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The roster as of a business day; onLeaveOnly keeps the employed people who are on unpaid leave that day (status is then EMPLOYED or empty); visibility and filters are applied before paging. */
public interface EmployeeRosterQueries {
    List<Row> list(Visible visible, @Nullable OrgUnitId orgUnit, @Nullable EmploymentStatus status, boolean onLeaveOnly, @Nullable EmployeeId after, int limit);

    Optional<Row> find(Visible visible, EmployeeId employee);

    boolean exists(EmployeeId employee);

    record Visible(boolean everything, Set<OrgUnitId> orgUnits) {
        public Visible {
            orgUnits = Set.copyOf(orgUnits);
        }
    }

    record Row(EmployeeId employeeId, String employeeCode, String displayName, EmploymentStatus employmentStatus,
               @Nullable LocalDate hireDate, boolean onUnpaidLeave, List<AssignmentRow> assignments) {
        public Row {
            assignments = List.copyOf(assignments);
        }
    }

    record AssignmentRow(AssignmentKind assignmentKind, OrgUnitId orgUnitId, String orgUnitName,
                         @Nullable PositionId positionId, @Nullable String positionName,
                         @Nullable EmployeeId reportsToEmployeeId, @Nullable String reportsToDisplayName) {
    }
}
