package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.CommandReadViews;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class CommandReadResponses {
    private CommandReadResponses() {
    }

    record PersonnelReadResponse(UUID personnelCommandId, String commandKind, UUID employeeId, String displayName, String employeeCode, LocalDate effectiveDate,
                            @Nullable LocalDate lastWorkingDay, String status, UUID createdBy, Instant createdAt, @Nullable UUID withdrawnBy,
                            @Nullable Instant withdrawnAt, @Nullable String withdrawalReason, @Nullable UUID separationHoldId, @Nullable String holdStatus) {
        static PersonnelReadResponse of(CommandReadViews.Personnel view) {
            return new PersonnelReadResponse(view.personnelCommandId(), view.commandKind(), view.employeeId(), view.displayName(),
                    view.employeeCode(), view.effectiveDate(), view.lastWorkingDay(), view.status(),
                    view.createdBy(), view.createdAt(), view.withdrawnBy(), view.withdrawnAt(),
                    view.withdrawalReason(), view.separationHoldId(), view.holdStatus());
        }
    }

    record CountersignReadResponse(UUID attributionCountersignId, String requestKind, UUID legalEntityId, @Nullable String decisionKind) {
        static CountersignReadResponse of(CommandReadViews.Countersign view) {
            return new CountersignReadResponse(view.attributionCountersignId(), view.requestKind(), view.legalEntityId(), view.decisionKind());
        }
    }

    record MergeProgressReadResponse(int affectedEmployeeCount, int transferredEmployeeCount) {
        static MergeProgressReadResponse of(CommandReadViews.MergeProgress view) {
            return new MergeProgressReadResponse(view.affectedEmployeeCount(), view.transferredEmployeeCount());
        }
    }

    record StructureReadResponse(UUID orgStructureCommandId, String commandKind, String targetKind, @Nullable UUID targetOrgUnitId, @Nullable UUID targetPositionId,
                            LocalDate effectiveDate, String status, List<CountersignReadResponse> countersigns, @Nullable MergeProgressReadResponse mergeProgress) {
        static StructureReadResponse of(CommandReadViews.Structure view) {
            return new StructureReadResponse(view.orgStructureCommandId(), view.commandKind(), view.targetKind(), view.targetOrgUnitId(),
                    view.targetPositionId(), view.effectiveDate(), view.status(), view.countersigns().stream().map(CountersignReadResponse::of).toList(),
                    view.mergeProgress() == null ? null : MergeProgressReadResponse.of(view.mergeProgress()));
        }
    }

    record HoldReadResponse(UUID separationHoldId, UUID personnelCommandId, UUID employeeId, String displayName, String employeeCode,
                       @Nullable UUID orgUnitId, @Nullable String orgUnitName, LocalDate originalEffectiveDate, int remainingAvailableSuperAdministratorCount, String status) {
        static HoldReadResponse of(CommandReadViews.Hold view) {
            return new HoldReadResponse(view.separationHoldId(), view.personnelCommandId(), view.employeeId(), view.displayName(),
                    view.employeeCode(), view.orgUnitId(), view.orgUnitName(), view.originalEffectiveDate(),
                    view.remainingAvailableSuperAdministratorCount(), view.status());
        }
    }
}
