package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WecomUserId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Enters a personnel command. Nothing the server decides is here: the command id, the recorder, the status and the times are made by the use case
 * (ADR-0068 items 1.2, 1.6). A separation carries lastWorkingDay and no effectiveDate (it takes effect the day after, item 1.5); every other
 * kind carries effectiveDate. The target components are the ones of the kind: a transfer names what changes, a hire or reinstatement names all.
 * A hire of a new person carries no employeeId (the server makes it) and the profile that goes to the employee root; no other kind carries a profile.
 * There is no employee code: the system numbers it (user answer Q2 of 2026-10-30). Only a change of legal entity, a transfer or a correction may name
 * targetLegalEntityId; the cost legal entity before and after is the server's to compute (ADR-0073 item 3.4), so the caller never passes it.
 */
public record EnterPersonnelCommand(@Nullable EmployeeId employeeId, PersonnelCommandKind commandKind, @Nullable LocalDate effectiveDate,
                                    @Nullable LocalDate lastWorkingDay, @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId,
                                    @Nullable EmployeeId targetReportsToEmployeeId, @Nullable Boolean isTargetFeeEarner,
                                    @Nullable EmploymentPhaseKind targetEmploymentPhaseKind,
                                    @Nullable LegalEntityId targetLegalEntityId,
                                    @Nullable String displayName, @Nullable Gender gender, @Nullable String email, @Nullable WecomUserId wecomUserId) {

    /** A command that names no legal entity. */
    public EnterPersonnelCommand(@Nullable EmployeeId employeeId, PersonnelCommandKind commandKind, @Nullable LocalDate effectiveDate,
                                 @Nullable LocalDate lastWorkingDay, @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId,
                                 @Nullable EmployeeId targetReportsToEmployeeId, @Nullable Boolean isTargetFeeEarner,
                                 @Nullable EmploymentPhaseKind targetEmploymentPhaseKind,
                                 @Nullable String displayName, @Nullable Gender gender, @Nullable String email, @Nullable WecomUserId wecomUserId) {
        this(employeeId, commandKind, effectiveDate, lastWorkingDay, targetOrgUnitId, targetPositionId, targetReportsToEmployeeId, isTargetFeeEarner,
                targetEmploymentPhaseKind, null, displayName, gender, email, wecomUserId);
    }
}
