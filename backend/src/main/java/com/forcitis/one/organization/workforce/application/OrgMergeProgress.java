package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /** The merges in progress whose merged unit holds the employee on the merge day. */
    List<OrgStructureCommandId> holding(EmployeeId employee) {
        return commands.mergesInProgressOf(employee);
    }

    /** The merges in progress that hold the employee, their merged units locked; call before the employee row is locked. */
    List<OrgStructureCommandId> lockHolding(EmployeeId employee) {
        List<OrgStructureCommandId> holding = holding(employee);
        for (OrgStructureCommandId id : holding) {
            commands.lockOrgUnit(commands.find(id).orElseThrow().targetOrgUnitId());
        }
        return holding;
    }

    /** The merges that are pending or in progress, their merged units locked; call before the employee row is locked. */
    List<OrgStructureCommand> lockOpen() {
        List<OrgStructureCommand> open = new ArrayList<>();
        for (OrgStructureCommandId id : commands.openMerges()) {
            OrgStructureCommand merge = commands.find(id).orElseThrow();
            commands.lockOrgUnit(merge.targetOrgUnitId());
            open.add(commands.find(id).orElseThrow());
        }
        return open;
    }

    /**
     * The merges the employee is placed in the way of (one-design ruling on #1098, S1a): one in progress whose merged unit holds the person on the merge day, so the person
     * is not among those it counted, or a pending one whose unit the person joins only after the merge day. Compared before and after a command writes versions that name no unit.
     */
    Set<OrgStructureCommandId> placedBy(List<OrgStructureCommand> open, EmployeeId employee) {
        Set<OrgStructureCommandId> placed = new HashSet<>();
        for (OrgStructureCommand merge : open) {
            boolean in = merge.status() == OrgCommandStatus.IN_PROGRESS
                    ? commands.employedIn(merge.targetOrgUnitId(), merge.effectiveDate()).contains(employee)
                    : commands.arrivalsAfter(merge.targetOrgUnitId(), merge.effectiveDate()).stream().anyMatch(a -> a.employeeId().equals(employee));
            if (in) {
                placed.add(merge.id());
            }
        }
        return placed;
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
