package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTree;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The scope freeze of an org-structure command that awaits the countersign (docs/architecture/proposals/org-structure-commands.md section 5 rule 3): until it is countersigned or withdrawn, a later-dated command
 * that touches its scope is refused, because the basis is computed on the effective date only and cannot see what a later one would change. The scope is the target unit
 * and every unit below it on the effective date (a merge: also the receiving unit) and the employees of the basis. Earlier-dated commands are allowed; the comparison
 * at the last approval catches what they change on the effective date.
 */
@Component
class OrgCountersignFreeze {
    private final OrgStructureCommandRepository commands;

    OrgCountersignFreeze(OrgStructureCommandRepository commands) {
        this.commands = commands;
    }

    /**
     * The waiting command keeps the unit rows of its scope and the employee rows of its basis locked until it commits, in the order a personnel entry takes them (unit, then
     * employee): a personnel entry that touches the scope waits here and then sees the waiting command, so the freeze cannot be missed by two entries that overlap in time.
     */
    void holdScope(OrgStructureCommand waiting, List<OrgCountersignBasis> basis) {
        scopeOf(waiting, new OrgTree(commands.currentUnitRows())).stream().sorted().forEach(unit -> commands.lockOrgUnit(new OrgUnitId(unit)));
        basis.stream().map(row -> row.employeeId().value()).sorted().forEach(employee -> commands.lockEmployee(new EmployeeId(employee)));
    }

    /** An org-structure command about a unit: refused when it is dated after a waiting command and its target (a reparent: also the new parent; a merge: also the receiving unit) is in that command's scope. */
    void requireNotFrozen(OrgStructureCommand entering) {
        OrgUnitId target = entering.targetOrgUnitId();
        List<OrgStructureCommand> waiting = commands.awaitingCountersign();
        if (target == null || waiting.isEmpty()) {
            return;
        }
        OrgTree tree = new OrgTree(commands.currentUnitRows());
        for (OrgStructureCommand frozen : waiting) {
            if (!entering.effectiveDate().isAfter(frozen.effectiveDate())) {
                continue;
            }
            Set<UUID> scope = scopeOf(frozen, tree);
            boolean touches = scope.contains(target.value()) && entering.kind() != OrgStructureCommandKind.CREATE
                    || entering.kind() == OrgStructureCommandKind.REPARENT && entering.targetParentOrgUnitId() != null && scope.contains(entering.targetParentOrgUnitId().value())
                    || entering.kind() == OrgStructureCommandKind.MERGE && entering.mergeIntoOrgUnitId() != null && scope.contains(entering.mergeIntoOrgUnitId().value());
            if (touches) {
                throw frozen(frozen);
            }
        }
    }

    /** A personnel command: refused when it is dated after a waiting command and is about an employee of its basis or into a unit of its scope. A merge's own transfers are exempt. */
    void requireNotFrozen(PersonnelCommand entering) {
        List<OrgStructureCommand> waiting = commands.awaitingCountersign();
        if (waiting.isEmpty() || entering.orgStructureCommandId() != null) {
            return;
        }
        OrgTree tree = new OrgTree(commands.currentUnitRows());
        for (OrgStructureCommand frozen : waiting) {
            if (!entering.effectiveDate().isAfter(frozen.effectiveDate())) {
                continue;
            }
            Set<EmployeeId> basis = new HashSet<>(commands.basisOf(frozen.id()).stream().map(OrgCountersignBasis::employeeId).toList());
            boolean touches = basis.contains(entering.employeeId())
                    || entering.targetOrgUnitId() != null && scopeOf(frozen, tree).contains(entering.targetOrgUnitId().value());
            if (touches) {
                throw frozen(frozen);
            }
        }
    }

    private static Set<UUID> scopeOf(OrgStructureCommand frozen, OrgTree tree) {
        Set<UUID> scope = new HashSet<>(tree.subtreeOn(frozen.targetOrgUnitId().value(), frozen.effectiveDate()));
        if (frozen.mergeIntoOrgUnitId() != null) {
            scope.add(frozen.mergeIntoOrgUnitId().value());
        }
        return scope;
    }

    private static DomainException frozen(OrgStructureCommand frozen) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT,
                "org-structure command " + frozen.id() + " awaits the legal entity countersign for " + frozen.effectiveDate()
                        + "; nothing dated after it may touch its scope until it is countersigned or withdrawn");
    }
}
