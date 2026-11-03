package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** What the organisation would look like on and after the command's effective day if the org-structure command were written; the days before it are as stored. */
public final class OrgCommandResolutionSources implements LegalEntityResolutionSources {
    private final LegalEntityResolutionSources stored;
    private final OrgStructureCommand command;

    private OrgCommandResolutionSources(LegalEntityResolutionSources stored, OrgStructureCommand command) {
        this.stored = stored;
        this.command = command;
    }

    public static OrgCommandResolutionSources after(LegalEntityResolutionSources stored, OrgStructureCommand command) {
        return new OrgCommandResolutionSources(stored, command);
    }

    /** The sources with the employee's own attribution left out: the chain only, as when judging whether the drafter's own place in the organisation would change. */
    public static LegalEntityResolutionSources chainOnly(LegalEntityResolutionSources stored) {
        return new LegalEntityResolutionSources() {
            @Override
            public Optional<LegalEntityId> employeeAttribution(EmployeeId employee, LocalDate date) {
                return Optional.empty();
            }

            @Override
            public Optional<OrgUnitId> assignedUnit(EmployeeId employee, LocalDate date) {
                return stored.assignedUnit(employee, date);
            }

            @Override
            public Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date) {
                return stored.orgUnitAttribution(orgUnit, date);
            }

            @Override
            public Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date) {
                return stored.parentUnit(orgUnit, date);
            }
        };
    }

    @Override
    public Optional<LegalEntityId> employeeAttribution(EmployeeId employee, LocalDate date) {
        return stored.employeeAttribution(employee, date);
    }

    /** A merge puts the people of the merged unit into the receiving unit from the day on. */
    @Override
    public Optional<OrgUnitId> assignedUnit(EmployeeId employee, LocalDate date) {
        Optional<OrgUnitId> unit = stored.assignedUnit(employee, date);
        OrgUnitId into = command.mergeIntoOrgUnitId();
        return command.kind() == OrgStructureCommandKind.MERGE && taking(date) && into != null && unit.equals(Optional.ofNullable(command.targetOrgUnitId())) ? Optional.of(into) : unit;
    }

    @Override
    public Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date) {
        LegalEntityId assigned = command.targetLegalEntityId();
        if (command.kind() == OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY && taking(date) && orgUnit.equals(command.targetOrgUnitId()) && assigned != null) {
            return Optional.of(assigned);
        }
        return stored.orgUnitAttribution(orgUnit, date);
    }

    @Override
    public Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date) {
        OrgUnitId newParent = command.targetParentOrgUnitId();
        return command.kind() == OrgStructureCommandKind.REPARENT && taking(date) && orgUnit.equals(command.targetOrgUnitId()) && newParent != null
                ? Optional.of(newParent) : stored.parentUnit(orgUnit, date);
    }

    private boolean taking(@Nullable LocalDate date) {
        return date != null && !date.isBefore(command.effectiveDate());
    }
}
