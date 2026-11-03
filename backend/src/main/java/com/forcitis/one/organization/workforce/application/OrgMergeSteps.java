package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The three transactions of a merge run: its start, one person's move, its finish. Each takes the root row of the merged org unit first and the employee
 * row after it (lock order: org unit root, then employee root), so a concurrent last approval and an execution serialize on the unit. The person's move
 * is a transfer entered through the personnel command service, the core-employment partition's own entry (DD amendment C of #907).
 */
@Component
class OrgMergeSteps {

    private final OrgStructureCommandRepository commands;
    private final OrgStructureRules rules;
    private final OrgPlans plans;
    private final PersonnelCommandService personnel;
    private final AuditLog audit;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OrgMergeSteps(OrgStructureCommandRepository commands, OrgStructureRules rules, OrgPlans plans, PersonnelCommandService personnel, AuditLog audit,
                  DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.rules = rules;
        this.plans = plans;
        this.personnel = personnel;
        this.audit = audit;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    /** Starts a pending merge that is due (the people of that moment are counted once), or finds who is left of one in progress; a finished merge has nobody left. */
    @Transactional
    public List<EmployeeId> start(OrgStructureCommandId id, UUID actor) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        commands.lockOrgTree();
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() == OrgCommandStatus.EFFECTIVE) {
            return List.of();
        }
        if (merge.status() == OrgCommandStatus.WITHDRAWN) {
            throw notAllowed("the merge was withdrawn");
        }
        List<EmployeeId> people = commands.employedIn(merge.targetOrgUnitId(), merge.effectiveDate());
        if (merge.status() == OrgCommandStatus.PENDING) {
            if (merge.effectiveDate().isAfter(today)) {
                throw notAllowed("the merge is due on " + merge.effectiveDate());
            }
            rules.checkMergeStillPossible(merge);
            long loaded = merge.version();
            String before = merge.status().name();
            merge.startMerge(people.size());
            commands.update(merge, now, loaded);
            commands.insertTransition(merge.id(), loaded + 1, before, merge.status().name(), ActorType.SYSTEM, actor, today, now, correlation());
        }
        return people;
    }

    /** Moves one person into the receiving unit; one who something else has moved out since the list was made is counted as done. */
    @Transactional
    public void move(OrgStructureCommandId id, EmployeeId employee, UUID actor) {
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() != OrgCommandStatus.IN_PROGRESS) {
            return;
        }
        long loaded = merge.version();
        if (commands.employedIn(merge.targetOrgUnitId(), merge.effectiveDate()).contains(employee)) {
            personnel.enter(new EnterPersonnelCommand(employee, PersonnelCommandKind.TRANSFER, merge.effectiveDate(), null, merge.mergeIntoOrgUnitId(), null, null, null, null,
                    null, null, null, null), new UserId(actor), merge.id());
        }
        merge.recordTransferred();
        commands.update(merge, clock.instant(), loaded);
    }

    /** Revokes the merged unit once nobody is left in it; whoever is counted and no longer there has been moved, by the merge or by another command. The merge is then effective. */
    @Transactional
    public void finish(OrgStructureCommandId id, UUID actor) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        OrgStructureCommand merge = lockedMerge(id);
        if (merge.status() == OrgCommandStatus.EFFECTIVE) {
            return;
        }
        if (!commands.employedIn(merge.targetOrgUnitId(), merge.effectiveDate()).isEmpty()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_MERGE_INCOMPLETE, ErrorKind.CONFLICT, "somebody is still in the merged unit");
        }
        long loaded = merge.version();
        String before = merge.status().name();
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.org-structure-command.completed", null, null, AuditSubject.of(AuditSubjectKind.ORG_UNIT, merge.targetOrgUnitId().value()),
                "command " + merge.id() + " " + merge.kind() + " effective " + merge.effectiveDate()));
        OrgTimeline timeline = commands.unitTimeline(merge.targetOrgUnitId());
        merge.countRemainingAsMovedOut();
        merge.completeMerge(now);
        commands.insertUnitVersions(merge.targetOrgUnitId(), plans.forMergeCompletion(merge, timeline, auditEntry), merge.id(), now);
        commands.update(merge, now, loaded);
        commands.insertTransition(merge.id(), loaded + 1, before, merge.status().name(), ActorType.SYSTEM, actor, today, now, correlation());
        merge.recordEffect();
        events.publish(merge);
    }

    private OrgStructureCommand lockedMerge(OrgStructureCommandId id) {
        OrgStructureCommand found = commands.find(id).orElseThrow(OrgMergeSteps::notFound);
        if (found.kind() != OrgStructureCommandKind.MERGE) {
            throw notAllowed("the command is not a merge");
        }
        commands.lockOrgUnit(found.targetOrgUnitId());
        return commands.find(id).orElseThrow(OrgMergeSteps::notFound);
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such org-structure command");
    }

    private static DomainException notAllowed(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, message);
    }
}
