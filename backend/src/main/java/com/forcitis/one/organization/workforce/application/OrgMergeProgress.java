package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * How many of the people a merge counted are out of the merged unit (one-design ruling on #910 QF1 (b)): judged on the versions, whoever moved them and whenever it was
 * dated, so a command that takes a held person out counts them and a merge whose unit is empty ends. A command about a person a merge in progress holds locks the merged
 * unit's row before the employee row, the order of the merge's own steps.
 */
@Component
class OrgMergeProgress {
    private final OrgStructureCommandRepository commands;
    private final OrgMergeCompletion completion;
    private final Clock clock;

    OrgMergeProgress(OrgStructureCommandRepository commands, OrgMergeCompletion completion, Clock clock) {
        this.commands = commands;
        this.completion = completion;
        this.clock = clock;
    }

    /** The merges in progress that hold the employee, their merged units locked; call before the employee row is locked. */
    List<OrgStructureCommandId> lockHolding(EmployeeId employee) {
        List<OrgStructureCommandId> holding = commands.mergesInProgressOf(employee);
        for (OrgStructureCommandId id : holding) {
            commands.lockOrgUnit(commands.find(id).orElseThrow().targetOrgUnitId());
        }
        return holding;
    }

    /**
     * Whether the transfer a countersigned merge generates for one person needs no countersign of its own (ADR-0073 guard 1, design note section 5 rule 4): only when the person's
     * cost legal entity before and after, recomputed at execution, is what the merge's basis row says its finance heads approved. A person without a basis row has no approved
     * change, so a change found at execution waits for its own countersign.
     */
    boolean approved(OrgStructureCommandId merge, EmployeeId employee, CountersignBases.Basis found) {
        return commands.basisOf(merge).stream()
                .anyMatch(row -> row.employeeId().equals(employee) && row.previous().equals(found.previous()) && row.next().equals(found.next()));
    }

    /** After the versions of a command are written: counts who is out of each merge's unit and ends a merge whose unit is empty. */
    void recount(List<OrgStructureCommandId> merges) {
        for (OrgStructureCommandId id : merges) {
            OrgStructureCommand merge = commands.find(id).orElseThrow();
            if (merge.status() == OrgCommandStatus.IN_PROGRESS) {
                recount(merge);
            }
        }
    }

    /** The merge is locked and in progress. */
    void recount(OrgStructureCommand merge) {
        int left = commands.employedIn(merge.targetOrgUnitId(), merge.effectiveDate()).size();
        if (merge.recordRemaining(left)) {
            commands.update(merge, clock.instant(), merge.version());
        }
        if (left == 0 && completion.unitIsEmpty(merge)) {
            // The update above moved the stored version on: the merge is read again for the version its completion checks.
            completion.finish(commands.find(merge.id()).orElseThrow(), SystemActors.idOf(OrgMergeExecution.ACTOR));
        }
    }
}
