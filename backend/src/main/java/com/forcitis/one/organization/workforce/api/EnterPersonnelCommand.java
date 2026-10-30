package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Enters a personnel command. Nothing the server decides is here: the command id, the recorder, the status and the times are made by the use case
 * (ADR-0068 items 1.2, 1.6). A separation carries lastWorkingDay and no effectiveDate (it takes effect the day after, item 1.5); every other
 * kind carries effectiveDate. The target components are the ones of the kind: a transfer names what changes, a hire or reinstatement names all.
 */
public record EnterPersonnelCommand(EmployeeId employeeId, PersonnelCommandKind commandKind, @Nullable LocalDate effectiveDate,
                                    @Nullable LocalDate lastWorkingDay, @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId,
                                    @Nullable EmployeeId targetReportsToEmployeeId, @Nullable Boolean isTargetFeeEarner,
                                    @Nullable EmploymentPhaseKind targetEmploymentPhaseKind) {
}
