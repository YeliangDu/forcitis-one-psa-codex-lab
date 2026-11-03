package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgCommandResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTree;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Which employees' cost legal entity an org-structure command changes (ADR-0073 item 3.4, the org-structure commands design note section 5 rule 1): those employed on the effective date whose primary
 * assignment that day is in the unit or below it (a merge: in the merged unit), compared before and after the command with the same resolution as everywhere
 * (the employee's own attribution first, else the nearest department attribution up the chain). Only an assignment of a legal entity, a reparent and a merge can change it.
 */
@Component
class OrgCountersignBases {
    private final LegalEntityResolutionSources sources;
    private final OrgStructureCommandRepository commands;
    private final AccountEmployees accountEmployees;

    OrgCountersignBases(LegalEntityResolutionSources sources, OrgStructureCommandRepository commands, AccountEmployees accountEmployees) {
        this.sources = sources;
        this.commands = commands;
        this.accountEmployees = accountEmployees;
    }

    static boolean mayChangeCostLegalEntity(OrgStructureCommand command) {
        return command.targetOrgUnitId() != null && switch (command.kind()) {
            case ASSIGN_LEGAL_ENTITY, REPARENT, MERGE -> true;
            default -> false;
        };
    }

    List<OrgCountersignBasis> of(OrgStructureCommand command) {
        if (!mayChangeCostLegalEntity(command)) {
            return List.of();
        }
        LocalDate day = command.effectiveDate();
        LegalEntityResolutionSources after = OrgCommandResolutionSources.after(sources, command);
        List<OrgCountersignBasis> basis = new ArrayList<>();
        for (EmployeeId employee : affected(command)) {
            Optional<LegalEntityId> before = LegalEntityResolution.resolve(sources, employee, day);
            Optional<LegalEntityId> next = LegalEntityResolution.resolve(after, employee, day);
            if (before.isPresent() && next.isEmpty()) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_LEGAL_ENTITY_CONFLICT, ErrorKind.UNPROCESSABLE,
                        "the command would leave an employee without a cost legal entity on " + day);
            }
            if (before.isPresent() && next.isPresent() && !before.get().equals(next.get())) {
                basis.add(new OrgCountersignBasis(employee, before.get(), next.get()));
            }
        }
        return basis;
    }

    /** Nobody (drafter or decider) changes the legal entity their own place in the organisation belongs to (ADR-0027 section 2), the chain above them included, whatever their own attribution says. */
    void requireUserUnaffected(OrgStructureCommand command, UserId user) {
        if (!mayChangeCostLegalEntity(command)) {
            return;
        }
        Optional<EmployeeId> own = accountEmployees.employeeOf(user);
        if (own.isEmpty()) {
            return;
        }
        LocalDate day = command.effectiveDate();
        LegalEntityResolutionSources chainBefore = OrgCommandResolutionSources.withoutOwnAttribution(sources);
        LegalEntityResolutionSources chainAfter = OrgCommandResolutionSources.withoutOwnAttribution(OrgCommandResolutionSources.after(sources, command));
        Optional<LegalEntityId> before = LegalEntityResolution.resolve(chainBefore, own.get(), day);
        Optional<LegalEntityId> next = LegalEntityResolution.resolve(chainAfter, own.get(), day);
        if (before.isPresent() && next.isPresent() && !before.get().equals(next.get())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "the legal entity of your own department chain would change; another person enters or decides this command");
        }
    }

    private List<EmployeeId> affected(OrgStructureCommand command) {
        OrgUnitId target = command.targetOrgUnitId();
        LocalDate day = command.effectiveDate();
        if (command.kind() == OrgStructureCommandKind.MERGE) {
            return commands.employedIn(target, day);
        }
        List<EmployeeId> employees = new ArrayList<>();
        for (UUID unit : new OrgTree(commands.currentUnitRows()).subtreeOn(target.value(), day)) {
            employees.addAll(commands.employedIn(new OrgUnitId(unit), day));
        }
        return employees;
    }
}
