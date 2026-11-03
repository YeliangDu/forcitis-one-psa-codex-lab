package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * What an org-structure command writes to the unit and position timelines, and takes out again: the version rows of its plan and, for the assignment of a legal entity,
 * the unit's attribution period. Used where a command takes effect, at entry or at the last countersign, and where a countersigned one is withdrawn.
 */
@Component
class OrgCommandWrites {
    private final OrgStructureCommandRepository commands;
    private final OrgPlans plans;

    OrgCommandWrites(OrgStructureCommandRepository commands, OrgPlans plans) {
        this.commands = commands;
        this.plans = plans;
    }

    /** The timeline the command extends or takes back; none for a merge (nothing is written at entry) and for the create of a unit or position (it starts empty). */
    @Nullable OrgTimeline timelineOf(OrgStructureCommand command) {
        if (command.kind() == OrgStructureCommandKind.MERGE) {
            return null;
        }
        if (command.kind() == OrgStructureCommandKind.CREATE && command.status() != OrgCommandStatus.WITHDRAWN) {
            return new OrgTimeline(List.of(), Map.of());
        }
        return command.targetPositionId() != null ? commands.positionTimeline(command.targetPositionId()) : commands.unitTimeline(command.targetOrgUnitId());
    }

    void writeVersions(OrgStructureCommand command, VersionPlan plan, Instant now) {
        if (plan.isEmpty()) {
            return;
        }
        if (command.targetPositionId() != null) {
            commands.insertPositionVersions(command.targetPositionId(), plan, command.id(), now);
        } else {
            commands.insertUnitVersions(command.targetOrgUnitId(), plan, command.id(), now);
        }
    }

    /** The command takes effect: its plan, and the unit's new legal entity from its day. */
    void apply(OrgStructureCommand command, VersionPlan plan, AuditEntryId audit, Instant now) {
        writeVersions(command, plan, now);
        if (command.kind() == OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY) {
            commands.attributeUnit(command.targetOrgUnitId(), command.targetLegalEntityId(), command.effectiveDate(), command.id(), audit, now);
        }
    }

    /** A countersigned command takes effect at the last approval: its plan is made on the timeline as it stands then. */
    void applyAfterCountersign(OrgStructureCommand command, AuditEntryId audit, Instant now) {
        apply(command, planNow(command, audit), audit, now);
    }

    VersionPlan planNow(OrgStructureCommand command, AuditEntryId audit) {
        OrgTimeline timeline = timelineOf(command);
        return timeline == null ? VersionPlan.empty() : plans.forEntry(command, timeline, audit);
    }

    /** The command is withdrawn: its version rows and attribution periods are taken back. */
    void takeBack(OrgStructureCommand command, AuditEntryId audit, Instant now) {
        OrgTimeline timeline = timelineOf(command);
        if (timeline != null) {
            writeVersions(command, plans.forWithdrawal(command, timeline), now);
        }
        boolean attributed = command.kind() == OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY || command.kind() == OrgStructureCommandKind.CREATE && command.targetLegalEntityId() != null;
        if (attributed) {
            commands.voidAttribution(command.targetOrgUnitId(), command.id(), audit, now);
        }
    }
}
