package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommands;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PreviewOrgUnitMerge;
import com.forcitis.one.organization.workforce.api.WithdrawOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.OrgTimeline;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering and withdrawing org-structure commands (ADR-0068 item 4). One transaction holds the unit's root row locked, the cross-row rules, the command,
 * the version rows (W2 design section 2), the transition, the audit entry and, when the command takes effect, the event. The permission is checked before
 * anything about the content is read. A merge writes nothing but its command when entered; {@link OrgMergeExecution} moves its people later.
 */
@Component
class OrgStructureCommandService implements OrgStructureCommands {
    static final String PERMISSION = "organization.workforce:org-structure-command:create";
    static final String AUDIT_ENTERED = "organization.workforce.org-structure-command.entered";
    static final String AUDIT_WITHDRAWN = "organization.workforce.org-structure-command.withdrawn";

    private final OrgStructureCommandRepository commands;
    private final OrgPlans plans;
    private final OrgStructureRules rules;
    private final OrgMergeExecution merges;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OrgStructureCommandService(OrgStructureCommandRepository commands, OrgPlans plans, OrgStructureRules rules, OrgMergeExecution merges, AuditLog audit,
                               DomainEvents events, CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.plans = plans;
        this.rules = rules;
        this.merges = merges;
        this.audit = audit;
        this.events = events;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public OrgStructureCommandId enter(EnterOrgStructureCommand request) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        OrgStructureCommand command = OrgStructureCommand.enter(new OrgStructureCommandId(ids.next()), request, ids.next(), by, now, today);
        OrgUnitId unit = command.targetOrgUnitId();
        boolean creates = command.kind() == OrgStructureCommandKind.CREATE;
        if (unit != null && !creates) {
            lockOrExplain(unit);
        } else if (command.targetPositionId() != null && !creates && !commands.positionExists(command.targetPositionId())) {
            throw notAllowed("the position does not exist");
        }
        OrgTimeline timeline = timelineOf(command);
        AuditEntryId auditEntry = audit.record(enteredAudit(command, timeline, by));
        VersionPlan plan = timeline == null ? VersionPlan.empty() : plans.forEntry(command, timeline, auditEntry);
        rules.check(command, plan);
        if (creates) {
            insertTarget(command, now);
        }
        commands.insert(command);
        commands.insertTransition(command.id(), command.version(), null, command.status().name(), ActorType.USER, by.value(), today, now, correlation());
        writeVersions(command, plan, now);
        if (creates && command.targetLegalEntityId() != null) {
            commands.insertInitialAttribution(command.targetOrgUnitId(), command.targetLegalEntityId(), command.effectiveDate(), command.id(), auditEntry, now);
        }
        if (command.takesEffectAtEntry()) {
            command.recordEffect();
            events.publish(command);
        }
        return command.id();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public void withdraw(WithdrawOrgStructureCommand request) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        OrgStructureCommand found = commands.find(request.orgStructureCommandId()).orElseThrow(OrgStructureCommandService::notFound);
        if (found.targetOrgUnitId() != null) {
            commands.lockOrgUnit(found.targetOrgUnitId());
        }
        OrgStructureCommand command = commands.find(request.orgStructureCommandId()).orElseThrow(OrgStructureCommandService::notFound);
        long loadedVersion = command.version();
        OrgCommandStatus before = command.status();
        command.withdraw(by, request.reason(), now);
        OrgTimeline timeline = timelineOf(command);
        if (timeline != null) {
            writeVersions(command, plans.forWithdrawal(command, timeline), now);
        }
        if (command.kind() == OrgStructureCommandKind.CREATE && command.targetLegalEntityId() != null) {
            commands.updateInitialAttributionVoided(command.targetOrgUnitId(), command.id(), now);
        }
        commands.update(command, now, loadedVersion);
        commands.insertTransition(command.id(), loadedVersion + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, correlation());
        audit.record(new AuditEntry(AUDIT_WITHDRAWN, by, null, subjectOf(command), "command " + command.id() + " " + command.kind() + "; reason: "
                + command.withdrawalReason().replaceAll("\\p{Cntrl}", " ")));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public List<EmployeeId> previewMerge(PreviewOrgUnitMerge preview) {
        if (!commands.orgUnitExists(preview.targetOrgUnitId()) || !commands.orgUnitExists(preview.mergeIntoOrgUnitId())) {
            throw notAllowed("the org unit does not exist");
        }
        return commands.employedIn(preview.targetOrgUnitId(), preview.effectiveDate());
    }

    @Override
    public void executeMerge(OrgStructureCommandId id) {
        merges.execute(id);
    }

    private void lockOrExplain(OrgUnitId unit) {
        if (!commands.lockOrgUnit(unit)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND, ErrorKind.NOT_FOUND, "the org unit does not exist");
        }
    }

    /** The timeline the command extends or takes back; none for a merge (nothing is written at entry) and for the create of a unit or position (it starts empty). */
    private @Nullable OrgTimeline timelineOf(OrgStructureCommand command) {
        if (command.kind() == OrgStructureCommandKind.MERGE) {
            return null;
        }
        if (command.kind() == OrgStructureCommandKind.CREATE && command.status() != OrgCommandStatus.WITHDRAWN) {
            return new OrgTimeline(List.of(), Map.of());
        }
        return command.targetPositionId() != null ? commands.positionTimeline(command.targetPositionId()) : commands.unitTimeline(command.targetOrgUnitId());
    }

    private void insertTarget(OrgStructureCommand command, Instant now) {
        if (command.targetOrgUnitId() != null) {
            commands.insertOrgUnit(command, now);
        } else {
            commands.insertPosition(command, now);
        }
    }

    private void writeVersions(OrgStructureCommand command, VersionPlan plan, Instant now) {
        if (plan.isEmpty()) {
            return;
        }
        if (command.targetPositionId() != null) {
            commands.insertPositionVersions(command.targetPositionId(), plan, command.id(), now);
        } else {
            commands.insertUnitVersions(command.targetOrgUnitId(), plan, command.id(), now);
        }
    }

    /** The entry audit names each field the command changes with its value before and after, in the detail (ADR-0073 item 2.3). */
    private AuditEntry enteredAudit(OrgStructureCommand c, @Nullable OrgTimeline timeline, UserId by) {
        StringBuilder detail = new StringBuilder("command " + c.id() + " " + c.kind() + " effective " + c.effectiveDate());
        Map<String, Object> after = c.targetPositionId() != null ? OrgEffects.positionValues(c) : OrgEffects.unitValues(c);
        if (c.kind() != OrgStructureCommandKind.CREATE) {
            Map<String, Object> inForce = timeline == null ? Map.of() : timeline.current().stream().filter(row -> row.covers(c.effectiveDate())).map(VersionRow::values)
                    .findFirst().orElse(Map.of());
            after.forEach((column, value) -> detail.append("; ").append(column).append(": ").append(inForce.get(column)).append(" -> ").append(value));
        }
        return new AuditEntry(AUDIT_ENTERED, by, null, subjectOf(c), detail.toString().replaceAll("\\p{Cntrl}", " "));
    }

    private static AuditSubject subjectOf(OrgStructureCommand c) {
        return c.targetOrgUnitId() != null ? AuditSubject.of(AuditSubjectKind.ORG_UNIT, c.targetOrgUnitId().value()) : AuditSubject.of(AuditSubjectKind.ORG_STRUCTURE_COMMAND, c.id().value());
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such org-structure command");
    }

    private static DomainException notAllowed(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.UNPROCESSABLE, message);
    }
}
