package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The caller names the employee, the kind, the date and the targets of the kind; who entered it, the status and the times are the server's (ADR-0068 item 1.2). */
record EnterPersonnelCommandRequest(@Nullable UUID employeeId, @Nullable PersonnelCommandKind commandKind, @Nullable LocalDate effectiveDate,
                                    @Nullable LocalDate lastWorkingDay, @Nullable UUID targetOrgUnitId, @Nullable UUID targetPositionId,
                                    @Nullable UUID targetReportsToEmployeeId, @Nullable Boolean isTargetFeeEarner,
                                    @Nullable EmploymentPhaseKind targetEmploymentPhaseKind) {

    EnterPersonnelCommand toCommand() {
        if (employeeId == null || commandKind == null) {
            throw new InvalidRequestException("employeeId and commandKind are required");
        }
        return new EnterPersonnelCommand(new EmployeeId(employeeId), commandKind, effectiveDate, lastWorkingDay,
                targetOrgUnitId == null ? null : new OrgUnitId(targetOrgUnitId), targetPositionId == null ? null : new PositionId(targetPositionId),
                targetReportsToEmployeeId == null ? null : new EmployeeId(targetReportsToEmployeeId), isTargetFeeEarner, targetEmploymentPhaseKind);
    }
}
