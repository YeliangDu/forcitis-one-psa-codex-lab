package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Enters an org-structure command. Nothing the server decides is here: the command id, the id of a new org unit or position, the status and the
 * recorder are made by the use case (ADR-0068 item 4). The target components are the ones of the kind and only those: a CREATE names no target, every
 * other kind names the org unit or position it is about.
 */
public record EnterOrgStructureCommand(OrgStructureTargetKind targetKind, OrgStructureCommandKind commandKind, @Nullable OrgUnitId targetOrgUnitId,
                                       @Nullable PositionId targetPositionId, LocalDate effectiveDate, @Nullable String targetCode, @Nullable String targetName,
                                       @Nullable String targetShortName, @Nullable RegionKind targetRegionKind, @Nullable Boolean targetIsDeliveryUnit,
                                       @Nullable OrgUnitId targetParentOrgUnitId, @Nullable EmployeeId targetHeadEmployeeId,
                                       @Nullable EmployeeId targetReceivablesManagerEmployeeId, @Nullable OrgUnitId mergeIntoOrgUnitId,
                                       @Nullable LegalEntityId targetLegalEntityId) {
}
