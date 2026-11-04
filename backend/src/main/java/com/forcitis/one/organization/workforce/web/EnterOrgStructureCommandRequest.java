package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.RegionKind;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The caller names the kind, the date and the components of the kind; the command id, the id of a new unit or position, the status and the recorder are the server's (ADR-0068 item 4). */
record EnterOrgStructureCommandRequest(@Nullable OrgStructureTargetKind targetKind, @Nullable OrgStructureCommandKind commandKind,
                                       @Nullable UUID targetOrgUnitId, @Nullable UUID targetPositionId, @Nullable LocalDate effectiveDate,
                                       @Nullable String targetCode, @Nullable String targetName, @Nullable String targetShortName,
                                       @Nullable RegionKind targetRegionKind, @Nullable Boolean targetIsDeliveryUnit, @Nullable UUID targetParentOrgUnitId,
                                       @Nullable UUID targetHeadEmployeeId, @Nullable UUID targetReceivablesManagerEmployeeId,
                                       @Nullable UUID mergeIntoOrgUnitId, @Nullable UUID targetLegalEntityId) {

    EnterOrgStructureCommand toCommand() {
        if (targetKind == null) {
            throw new InvalidRequestException("targetKind is required");
        }
        if (commandKind == null) {
            throw new InvalidRequestException("commandKind is required");
        }
        if (effectiveDate == null) {
            throw new InvalidRequestException("effectiveDate is required");
        }
        return new EnterOrgStructureCommand(targetKind, commandKind, targetOrgUnitId == null ? null : new OrgUnitId(targetOrgUnitId),
                targetPositionId == null ? null : new PositionId(targetPositionId), effectiveDate, targetCode, targetName, targetShortName, targetRegionKind,
                targetIsDeliveryUnit, targetParentOrgUnitId == null ? null : new OrgUnitId(targetParentOrgUnitId),
                targetHeadEmployeeId == null ? null : new EmployeeId(targetHeadEmployeeId),
                targetReceivablesManagerEmployeeId == null ? null : new EmployeeId(targetReceivablesManagerEmployeeId),
                mergeIntoOrgUnitId == null ? null : new OrgUnitId(mergeIntoOrgUnitId), targetLegalEntityId == null ? null : new LegalEntityId(targetLegalEntityId));
    }
}
