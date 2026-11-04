package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface CommandReadQueries {
    record Position(Instant createdAt, UUID key) {
    }

    record Scope(boolean everything, Set<OrgUnitId> units) {
        public Scope {
            units = Set.copyOf(units);
        }
    }

    record Personnel(PersonnelCommandId personnelCommandId, PersonnelCommandKind commandKind, EmployeeId employeeId, String displayName, String employeeCode,
                     LocalDate effectiveDate, CommandStatus status, UserId createdBy, Instant createdAt, @Nullable UserId withdrawnBy,
                     @Nullable Instant withdrawnAt, @Nullable String withdrawalReason, @Nullable SeparationHoldId separationHoldId, @Nullable HoldStatus holdStatus) {
    }

    record Structure(OrgStructureCommandId orgStructureCommandId, OrgStructureCommandKind commandKind, OrgStructureTargetKind targetKind,
                     @Nullable OrgUnitId targetOrgUnitId, @Nullable PositionId targetPositionId, LocalDate effectiveDate, OrgCommandStatus status,
                     @Nullable Integer affectedEmployeeCount, @Nullable Integer transferredEmployeeCount, Instant createdAt) {
    }

    record Hold(SeparationHoldId separationHoldId, PersonnelCommandId personnelCommandId, EmployeeId employeeId, String displayName, String employeeCode,
                @Nullable OrgUnitId orgUnitId, @Nullable String orgUnitName, LocalDate originalEffectiveDate, HoldStatus status, Instant createdAt) {
    }

    List<Personnel> personnel(Scope scope, @Nullable CommandStatus status, @Nullable PersonnelCommandKind kind, @Nullable EmployeeId employee,
                              @Nullable Position after, int limit);

    Optional<Personnel> personnel(Scope scope, PersonnelCommandId id);

    Optional<EmployeeId> employeeOf(PersonnelCommandId id);

    record Countersign(AttributionCountersignId attributionCountersignId, CountersignRequestKind requestKind, LegalEntityId legalEntityId, CountersignStatus status) {
    }

    List<Countersign> countersigns(OrgStructureCommandId id);

    List<Structure> structures(@Nullable Position after, int limit);

    Optional<Structure> structure(OrgStructureCommandId id);

    List<Hold> holds(@Nullable Position after, int limit);

    Optional<Hold> hold(SeparationHoldId id);
}
