package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.BlockingArrival;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.organization.workforce.domain.OrgTree;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The cross-row rules of an org-structure command (ADR-0068 item 4 and the one-design rulings on #906), judged on the whole timeline: what the command
 * names exists and is in force, the day is free, a unit is dissolved or merged only when it is empty for good, and nothing is placed below a unit that is
 * going away or below its own descendants.
 */
@Component
class OrgStructureRules {
    private final OrgStructureCommandRepository commands;

    OrgStructureRules(OrgStructureCommandRepository commands) {
        this.commands = commands;
    }

    /** @param unitPlan what the command writes to its unit's timeline, for the cycle check; empty for the other kinds */
    void check(OrgStructureCommand c, VersionPlan unitPlan) {
        check(c, unitPlan, true);
    }

    /** The same rules for a command that is already entered and waits: it is not counted as an open command against itself. */
    void checkStillHolds(OrgStructureCommand c, VersionPlan unitPlan) {
        check(c, unitPlan, false);
    }

    private void check(OrgStructureCommand c, VersionPlan unitPlan, boolean entering) {
        LocalDate day = c.effectiveDate();
        if (c.targetPositionId() != null) {
            checkPosition(c, day);
            return;
        }
        OrgTree tree = new OrgTree(commands.currentUnitRows());
        OrgUnitId unit = c.targetOrgUnitId();
        switch (c.kind()) {
            case CREATE -> {
                if (c.targetParentOrgUnitId() != null) {
                    requireRoomBelow(c.targetParentOrgUnitId(), day, tree);
                }
                if (c.targetLegalEntityId() != null && !commands.legalEntityExists(c.targetLegalEntityId())) {
                    throw refused("the legal entity does not exist");
                }
                return;
            }
            case REPARENT -> {
                requireInForce(unit, day, tree);
                requireRoomBelow(c.targetParentOrgUnitId(), day, tree);
                tree.requireNoCycle(unit.value(), day, unitPlan);
            }
            case ASSIGN_LEGAL_ENTITY -> {
                requireInForce(unit, day, tree);
                if (!commands.legalEntityExists(c.targetLegalEntityId())) {
                    throw refused("the legal entity does not exist");
                }
                if (commands.hasAttributionAfter(unit, day)) {
                    throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_LEGAL_ENTITY_CONFLICT, ErrorKind.UNPROCESSABLE,
                            "the unit already has a legal entity period that starts on or after that day; a back-dated assignment is not supported yet");
                }
            }
            case ASSIGN_HEAD -> {
                requireInForce(unit, day, tree);
                requireEmployee(c.targetHeadEmployeeId());
            }
            case ASSIGN_RECEIVABLES_MANAGER -> {
                requireInForce(unit, day, tree);
                requireEmployee(c.targetReceivablesManagerEmployeeId());
            }
            case DISSOLVE -> {
                requireInForce(unit, day, tree);
                requireEmptyForGood(unit, day, tree);
                requireNoMergeArriving(unit, day);
            }
            case MERGE -> {
                requireInForce(unit, day, tree);
                requireReceiving(c.mergeIntoOrgUnitId(), day, tree);
                requireEmptyOfUnits(unit, day, tree);
                requireNoMergeIntoDatedOnOrAfter(unit, day);
                requireNoArrival(unit, day);
            }
            default -> requireInForce(unit, day, tree);
        }
        if (entering && commands.hasOpenCommand(unit, null, day)) {
            throw refused("the org unit already has an open command for " + day);
        }
    }

    /** Execution looks at the children once more: a unit may have been put below the merged one after the merge was entered (for example by a load). */
    void checkMergeStillPossible(OrgStructureCommand merge) {
        OrgTree tree = new OrgTree(commands.currentUnitRows());
        requireEmptyOfUnits(merge.targetOrgUnitId(), merge.effectiveDate(), tree);
        requireReceiving(merge.mergeIntoOrgUnitId(), merge.effectiveDate(), tree);
        requireNoArrival(merge.targetOrgUnitId(), merge.effectiveDate());
    }

    /** The merge moves the people of its day; somebody who joins the unit later would be left in a unit that is revoked from that day. */
    private void requireNoArrival(OrgUnitId unit, LocalDate day) {
        List<BlockingArrival> arrivals = commands.arrivalsAfter(unit, day);
        if (!arrivals.isEmpty()) {
            throw refused(describe(arrivals, day));
        }
    }

    /** Names who blocks the merge and which command put them there, so HR knows what to withdraw or re-enter (ids only). */
    static String describe(List<BlockingArrival> arrivals, LocalDate day) {
        StringBuilder text = new StringBuilder("these employees join the org unit after " + day + " and would be left in a revoked unit; withdraw or re-enter their commands first:");
        arrivals.stream().limit(20).forEach(a -> text.append(" employee ").append(a.employeeId()).append(" from ").append(a.startDate())
                .append(a.personnelCommandId() == null ? " (migrated row)" : " (command " + a.personnelCommandId() + ")").append(";"));
        if (arrivals.size() > 20) {
            text.append(" and ").append(arrivals.size() - 20).append(" more");
        }
        return text.toString();
    }

    private void checkPosition(OrgStructureCommand c, LocalDate day) {
        switch (c.kind()) {
            case CREATE -> {
                if (c.targetCode() != null && commands.positionCodeTaken(c.targetCode())) {
                    throw refused("the position code is taken; codes are company-unique and never reused");
                }
            }
            default -> {
                OrgTimeline timeline = commands.positionTimeline(c.targetPositionId());
                if (timeline.current().stream().noneMatch(row -> row.covers(day) && "ACTIVE".equals(row.values().get("status")))) {
                    throw refused("the position is not in force on " + day);
                }
                if (commands.hasHolderFrom(c.targetPositionId(), day)) {
                    throw refused("somebody holds the position on or after " + day);
                }
                if (commands.hasOpenCommand(null, c.targetPositionId(), day)) {
                    throw refused("the position already has an open command for " + day);
                }
            }
        }
    }

    private void requireInForce(OrgUnitId unit, LocalDate day, OrgTree tree) {
        if (!commands.orgUnitExists(unit)) {
            throw refused("the org unit does not exist");
        }
        if (!tree.activeOn(unit.value(), day)) {
            throw refused("the org unit is not in force on " + day);
        }
    }

    /** Something placed below the unit from the day on stays below it: the unit must exist, be in force from then on and not be about to be merged away. */
    private void requireRoomBelow(OrgUnitId parent, LocalDate day, OrgTree tree) {
        if (!commands.orgUnitExists(parent) || !tree.activeFrom(parent.value(), day)) {
            throw refused("the parent org unit does not exist or is not in force from " + day);
        }
        if (commands.hasOpenMerge(parent)) {
            throw refused("the parent org unit is about to be merged away");
        }
    }

    /**
     * The people of a merge are placed in the receiving unit on the merge day (one-design ruling on #1098, S2): it must be in force from then on and must not be going away
     * by a merge of its own on or before that day, nor be emptied already (a merge in progress). A merge of the receiving unit dated later is a chain: the people arrive first and move on.
     */
    private void requireReceiving(OrgUnitId receiving, LocalDate day, OrgTree tree) {
        if (!commands.orgUnitExists(receiving) || !tree.activeFrom(receiving.value(), day)) {
            throw refused("the parent org unit does not exist or is not in force from " + day);
        }
        if (commands.hasMergeAwayOnOrBefore(receiving, day)) {
            throw refused("the receiving org unit is merged away on or before " + day + ", or its merge is in progress");
        }
    }

    /** A merge dated on the same day or later puts its people into the unit; this merge, dated on or before that, would take the unit away first. */
    private void requireNoMergeIntoDatedOnOrAfter(OrgUnitId unit, LocalDate day) {
        if (commands.hasMergeIntoOnOrAfter(unit, day)) {
            throw refused("a merge into the org unit is dated on or after " + day + "; its people would arrive after the unit is merged away");
        }
    }

    /** People are about to arrive in the unit by a merge dated on or before the day: it cannot be dissolved or merged away on that day with them still to come. */
    private void requireNoMergeArriving(OrgUnitId unit, LocalDate day) {
        if (commands.hasOpenMergeInto(unit, day)) {
            throw refused("a merge into the org unit is due on or before " + day + "; its people are still to arrive");
        }
    }

    private void requireEmptyOfUnits(OrgUnitId unit, LocalDate day, OrgTree tree) {
        if (tree.hasActiveChildFrom(unit.value(), day)) {
            throw refused("the org unit has an active child unit on or after " + day + "; move the children first");
        }
    }

    private void requireEmptyForGood(OrgUnitId unit, LocalDate day, OrgTree tree) {
        requireEmptyOfUnits(unit, day, tree);
        if (commands.hasEmployedFrom(unit, day)) {
            throw refused("somebody is employed in the org unit on or after " + day);
        }
    }

    private void requireEmployee(com.forcitis.one.kernel.EmployeeId employee) {
        if (!commands.employeeExists(employee)) {
            throw refused("the employee does not exist");
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, message);
    }
}
