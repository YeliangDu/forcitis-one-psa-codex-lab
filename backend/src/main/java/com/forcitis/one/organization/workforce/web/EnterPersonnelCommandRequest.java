package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The caller names the employee, the kind, the date and the targets of the kind; who entered it, the status and the times are the server's (ADR-0068 item 1.2).
 * A hire of a new person names no employee and carries the profile; the employee code is never the caller's (it is numbered by the system).
 */
record EnterPersonnelCommandRequest(@Nullable UUID employeeId, @Nullable PersonnelCommandKind commandKind, @Nullable LocalDate effectiveDate,
                                    @Nullable LocalDate lastWorkingDay, @Nullable UUID targetOrgUnitId, @Nullable UUID targetPositionId,
                                    @Nullable UUID targetReportsToEmployeeId, @Nullable Boolean isTargetFeeEarner,
                                    @Nullable EmploymentPhaseKind targetEmploymentPhaseKind, @Nullable UUID targetLegalEntityId,
                                    @Nullable String displayName, @Nullable Gender gender, @Nullable String email, @Nullable String wecomUserId,
                                    @Nullable UUID targetAssignmentId, @Nullable UUID targetEmployerLegalEntityId, @Nullable UUID predecessorRelationshipId,
                                    @Nullable UUID employmentRelationshipId,
                                    @Nullable UUID actingCoveredOrgUnitId, @Nullable UUID actingCoveredPositionId,
                                    @Nullable UUID actingCoveredEmployeeId, @Nullable LocalDate actingEndsBeforeDate,
                                    @Nullable UUID targetActingDutyId, @Nullable String reason) {

    EnterPersonnelCommand toCommand() {
        if (commandKind == null) {
            throw new InvalidRequestException("commandKind is required");
        }
        if (wecomUserId != null && wecomUserId.isBlank()) {
            throw new InvalidRequestException("wecomUserId must not be blank; leave it out to have none");
        }
        return new EnterPersonnelCommand(employeeId == null ? null : new EmployeeId(employeeId), commandKind, effectiveDate, lastWorkingDay,
                targetOrgUnitId == null ? null : new OrgUnitId(targetOrgUnitId), targetPositionId == null ? null : new PositionId(targetPositionId),
                targetReportsToEmployeeId == null ? null : new EmployeeId(targetReportsToEmployeeId), isTargetFeeEarner, targetEmploymentPhaseKind,
                targetLegalEntityId == null ? null : new LegalEntityId(targetLegalEntityId), displayName, gender, email, wecomUserId == null ? null : new WecomUserId(wecomUserId),
                targetAssignmentId == null ? null : new AssignmentId(targetAssignmentId),
                targetEmployerLegalEntityId == null ? null : new LegalEntityId(targetEmployerLegalEntityId),
                predecessorRelationshipId == null ? null : new EmploymentRelationshipId(predecessorRelationshipId),
                employmentRelationshipId == null ? null : new EmploymentRelationshipId(employmentRelationshipId),
                actingCoveredOrgUnitId == null ? null : new OrgUnitId(actingCoveredOrgUnitId),
                actingCoveredPositionId == null ? null : new PositionId(actingCoveredPositionId),
                actingCoveredEmployeeId == null ? null : new EmployeeId(actingCoveredEmployeeId), actingEndsBeforeDate,
                targetActingDutyId == null ? null : new ActingDutyId(targetActingDutyId), reason);
    }
}
