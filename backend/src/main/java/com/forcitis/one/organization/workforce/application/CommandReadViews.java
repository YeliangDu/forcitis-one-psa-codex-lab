package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Position;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class CommandReadViews {
    private CommandReadViews() {
    }

    public record Filter(@Nullable CommandStatus status, @Nullable PersonnelCommandKind commandKind) {
        public static Filter of(@Nullable String status, @Nullable String commandKind) {
            try {
                return new Filter(status == null ? null : CommandStatus.ofStoredName(status)
                        .orElseThrow(() -> new InvalidRequestException("status must be a declared personnel command value")),
                        commandKind == null ? null : PersonnelCommandKind.valueOf(commandKind));
            } catch (IllegalArgumentException invalid) {
                throw new InvalidRequestException("status and commandKind must be declared personnel command values");
            }
        }
    }

    public record Personnel(UUID personnelCommandId, String commandKind, UUID employeeId, String displayName, String employeeCode, LocalDate effectiveDate,
                            @Nullable LocalDate lastWorkingDay, String status, UUID createdBy, Instant createdAt, @Nullable UUID withdrawnBy,
                            @Nullable Instant withdrawnAt, @Nullable String withdrawalReason, @Nullable UUID separationHoldId, @Nullable String holdStatus) {
        static Personnel of(CommandReadQueries.Personnel row) {
            return new Personnel(row.personnelCommandId().value(), row.commandKind().name(), row.employeeId().value(), row.displayName(), row.employeeCode(), row.effectiveDate(),
                    row.commandKind() == PersonnelCommandKind.SEPARATE ? row.effectiveDate().minusDays(1) : null, row.status().name(), row.createdBy().value(), row.createdAt(),
                    row.withdrawnBy() == null ? null : row.withdrawnBy().value(), row.withdrawnAt(), row.withdrawalReason(),
                    row.separationHoldId() == null ? null : row.separationHoldId().value(), row.holdStatus() == null ? null : row.holdStatus().name());
        }
    }

    public record Countersign(UUID attributionCountersignId, String requestKind, UUID legalEntityId, @Nullable String decisionKind) {
        static Countersign of(CommandReadQueries.Countersign request) {
            return new Countersign(request.attributionCountersignId().value(), request.requestKind().name(), request.legalEntityId().value(),
                    request.status() == CountersignStatus.OPEN ? null : request.status().name());
        }
    }

    public record MergeProgress(int affectedEmployeeCount, int transferredEmployeeCount) {
    }

    public record Structure(UUID orgStructureCommandId, String commandKind, String targetKind, @Nullable UUID targetOrgUnitId, @Nullable UUID targetPositionId,
                            LocalDate effectiveDate, String status, List<Countersign> countersigns, @Nullable MergeProgress mergeProgress) {
        static Structure of(CommandReadQueries.Structure row, List<CommandReadQueries.Countersign> requests) {
            Integer affected = row.affectedEmployeeCount();
            Integer transferred = row.transferredEmployeeCount();
            return new Structure(row.orgStructureCommandId().value(), row.commandKind().name(), row.targetKind().name(),
                    row.targetOrgUnitId() == null ? null : row.targetOrgUnitId().value(), row.targetPositionId() == null ? null : row.targetPositionId().value(),
                    row.effectiveDate(), row.status().name(), requests.stream().map(Countersign::of).toList(),
                    affected == null || transferred == null ? null : new MergeProgress(affected, transferred));
        }
    }

    public record Hold(UUID separationHoldId, UUID personnelCommandId, UUID employeeId, String displayName, String employeeCode,
                       @Nullable UUID orgUnitId, @Nullable String orgUnitName, LocalDate originalEffectiveDate, int remainingAvailableSuperAdministratorCount, String status) {
        static Hold of(CommandReadQueries.Hold row, int remaining) {
            return new Hold(row.separationHoldId().value(), row.personnelCommandId().value(), row.employeeId().value(), row.displayName(), row.employeeCode(),
                    row.orgUnitId() == null ? null : row.orgUnitId().value(), row.orgUnitName(), row.originalEffectiveDate(), remaining, row.status().name());
        }
    }

    static @Nullable Position position(@Nullable String value) {
        if (value == null) {
            return null;
        }
        int cut = value.indexOf('|');
        try {
            Instant at = Instant.parse(value.substring(0, cut));
            if (at.isBefore(Instant.parse("1970-01-01T00:00:00Z")) || at.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) {
                throw new IllegalArgumentException("cursor moment out of range");
            }
            return new CommandReadQueries.Position(at, Uuids.parseCanonical(value.substring(cut + 1)));
        } catch (DateTimeException | IllegalArgumentException | StringIndexOutOfBoundsException invalid) {
            throw new InvalidCursorException("the cursor key is not a command read position");
        }
    }

    static DomainException personnelNotFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such personnel command");
    }

    static DomainException structureNotFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such org structure command");
    }

    static DomainException holdNotFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such separation hold");
    }
}
