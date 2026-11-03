package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionTable;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * What every release of a held separation writes once it is allowed (ADR-0068 items 2.3 and 2.4): the versions are written at the original date
 * through the version mechanism; the command takes effect now when that date is not after today (with EmployeeSeparated), otherwise it waits as
 * pending for the due-date job. The hold, the transitions, the audit entry and SeparationHoldReleased go into the caller's transaction. The caller has
 * taken the separation lock, judged the release and marked the hold released. It is never held again.
 */
@Component
class HeldSeparationRelease {
    private static final String COMMAND = "PersonnelCommand";
    private static final String HOLD = "SeparationHold";

    private final PersonnelCommandRepository commands;
    private final PersonnelPlans timelines;
    private final LegalEntityResolutionSources legalEntities;
    private final AuditLog audit;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final BusinessZone zone;

    HeldSeparationRelease(PersonnelCommandRepository commands, PersonnelPlans timelines, LegalEntityResolutionSources legalEntities, AuditLog audit,
                          DomainEvents events, IdGenerator ids, BusinessZone zone) {
        this.commands = commands;
        this.timelines = timelines;
        this.legalEntities = legalEntities;
        this.audit = audit;
        this.events = events;
        this.ids = ids;
        this.zone = zone;
    }

    /** Who released: an account in the interface, or the operations command, whose actor is the system and whose people are only in the audit detail. */
    record Releaser(ActorType type, UUID id, @Nullable UserId account) {
        static Releaser user(UserId by) {
            return new Releaser(ActorType.USER, by.value(), by);
        }

        static Releaser operations(UUID systemActor) {
            return new Releaser(ActorType.SYSTEM, systemActor, null);
        }
    }

    /** The loaded versions are those read before the aggregates were changed; now is the instant the hold was marked released. */
    void execute(PersonnelCommand command, long commandVersion, SeparationHold hold, long holdVersion, Releaser releaser, Instant now,
                 String auditDetail) {
        LocalDate today = zone.dateOf(now);
        EmployeeId employee = command.employeeId();
        String commandBefore = command.status().name();
        command.releaseHeld(now, today);
        PersonnelHistory history = commands.load(employee);
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.separation-hold.released", releaser.account(), null,
                AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()), auditDetail));
        for (Map.Entry<VersionTable, VersionPlan> plan : timelines.forEntry(command, history, auditEntry).entrySet()) {
            commands.insertVersions(employee, plan.getKey(), plan.getValue(), command.id(), now);
        }
        commands.update(command, now, commandVersion);
        commands.updateHold(hold, now, holdVersion);
        UUID correlation = WorkContext.correlationId().orElseGet(ids::next);
        commands.insertTransition(COMMAND, command.id().value(), commandVersion + 1, commandBefore, command.status().name(), releaser.type(), releaser.id(), today, now,
                command.id(), correlation);
        commands.insertTransition(HOLD, hold.id().value(), hold.version(), "HELD", hold.status().name(), releaser.type(), releaser.id(), today, now, command.id(),
                correlation);
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, employee, command.effectiveDate().minusDays(1)).orElse(null);
        if (command.status() == CommandStatus.EFFECTIVE) {
            command.recordEffect(null, null, legalEntity);
        }
        command.recordReleased(hold.id(), releaser.account(), legalEntity);
        events.publish(command);
        commands.saveEventSequence(employee, command.eventSequenceAfterSave());
    }
}
