package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.BlockingArrival;
import com.forcitis.one.organization.workforce.api.HeldSeparation;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ends a merge in progress once nobody is left in the merged unit from the merge day on: the unit is revoked, the merge is effective and its event leaves. It runs inside the
 * transaction of whoever found the unit empty, with the merged unit's row already locked: the merge's own last step, or the personnel command that moved the last held person out.
 */
@Component
class OrgMergeCompletion {
    private final OrgStructureCommandRepository commands;
    private final OrgPlans plans;
    private final AuditLog audit;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OrgMergeCompletion(OrgStructureCommandRepository commands, OrgPlans plans, AuditLog audit, DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.plans = plans;
        this.audit = audit;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    boolean unitIsEmpty(OrgStructureCommand merge) {
        return !commands.hasEmployedFrom(merge.targetOrgUnitId(), merge.effectiveDate());
    }

    /** Whoever is counted and no longer there has been moved, by the merge or by another command. */
    void finish(OrgStructureCommand merge, UUID actor) {
        if (!unitIsEmpty(merge)) {
            List<BlockingArrival> arrivals = commands.arrivalsAfter(merge.targetOrgUnitId(), merge.effectiveDate());
            List<HeldSeparation> held = commands.heldSeparationsIn(merge.targetOrgUnitId(), merge.effectiveDate());
            StringBuilder text = new StringBuilder(arrivals.isEmpty() ? "somebody is still in the merged unit on the merge day" : OrgStructureRules.describe(arrivals, merge.effectiveDate()));
            if (!held.isEmpty()) {
                text.append("; their separation is held, transfer them out of the unit by hand:");
                held.stream().limit(20).forEach(h -> text.append(" employee ").append(h.employeeId()).append(" (held command ").append(h.personnelCommandId()).append(");"));
            }
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_MERGE_INCOMPLETE, ErrorKind.CONFLICT, text.toString());
        }
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        long loaded = merge.version();
        String before = merge.status().name();
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.org-structure-command.completed", null, null, AuditSubject.of(AuditSubjectKind.ORG_UNIT, merge.targetOrgUnitId().value()),
                "command " + merge.id() + " " + merge.kind() + " effective " + merge.effectiveDate()));
        OrgTimeline timeline = commands.unitTimeline(merge.targetOrgUnitId());
        merge.countRemainingAsTransferred();
        merge.completeMerge(now);
        commands.insertUnitVersions(merge.targetOrgUnitId(), plans.forMergeCompletion(merge, timeline, auditEntry), merge.id(), now);
        commands.update(merge, now, loaded);
        commands.insertTransition(merge.id(), loaded + 1, before, merge.status().name(), ActorType.SYSTEM, actor, today, now, WorkContext.correlationId().orElseGet(ids::next));
        merge.recordEffect();
        events.publish(merge);
    }
}
