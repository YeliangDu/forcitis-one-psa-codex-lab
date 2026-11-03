package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.organization.workforce.domain.VersionTable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The due-date step for one pending command (ADR-0068 items 1.8 and 2.3): on its effective date it becomes effective and its event, if the kind has
 * one, is published, once. Its versions were written when it was entered or released and are not touched, with one exception: a separation that was never held
 * is judged once more on its effective date (ADR-0068 item 2.2, #967), the leaver as of the last working day. When it must be held, the future separated version
 * is voided (the timeline as before the entry), the command becomes held with a hold, and SeparationHeld is published instead of EmployeeSeparated. A released
 * separation is not judged again, nor is one run after its date: it takes effect as written and an alert says that the job was late (item 1.8).
 * Nothing else changes: a command that is not pending any more (withdrawn meanwhile, or already done) is left as it is. Every command takes the
 * separation lock first, the order of the entry and the release, so the due day never interleaves with a decision that counts who has gone.
 */
@SystemActorName("organization.workforce.personnel.command.due")
@Component
public class ApplyDueCommand {
    private static final String ACTOR = ApplyDueCommand.class.getAnnotation(SystemActorName.class).value();
    private static final Logger LOG = LoggerFactory.getLogger(ApplyDueCommand.class);
    private static final String COMMAND = "PersonnelCommand";
    private static final String HOLD = "SeparationHold";

    private final PersonnelCommandRepository commands;
    private final PersonnelPlans timelines;
    private final SeparationHoldCheck holdCheck;
    private final LegalEntityResolutionSources legalEntities;
    private final CountersignRequests countersignRequests;
    private final AttributionCountersignRepository countersigns;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    ApplyDueCommand(PersonnelCommandRepository commands, PersonnelPlans timelines, SeparationHoldCheck holdCheck,
                    LegalEntityResolutionSources legalEntities, CountersignRequests countersignRequests, AttributionCountersignRepository countersigns, DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.timelines = timelines;
        this.holdCheck = holdCheck;
        this.legalEntities = legalEntities;
        this.countersignRequests = countersignRequests;
        this.countersigns = countersigns;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void execute(PersonnelCommandId id) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, ACTOR)) {
            commands.lockSeparationDecisions();
            Optional<PersonnelCommand> found = commands.find(id);
            if (found.isEmpty()) {
                return;
            }
            commands.lockEmployee(found.get().employeeId());
            PersonnelCommand command = commands.find(id).orElseThrow();
            if (command.status() != CommandStatus.PENDING || command.effectiveDate().isAfter(today)) {
                return;
            }
            UUID actor = SystemActors.idOf(ACTOR);
            if (command.kind() == PersonnelCommandKind.SEPARATE && commands.holdOf(command.id()).isEmpty()) {
                if (command.effectiveDate().equals(today)) {
                    if (holdCheck.holdsOnDueDay(command)) {
                        holdOnDueDay(command, actor, now, today);
                        return;
                    }
                } else {
                    LOG.error("ALERT organization.workforce separation {} due on {} was not judged on its day, the job ran on {}: it takes effect as written", id.value(),
                            command.effectiveDate(), today);
                }
            }
            long loaded = command.version();
            String before = command.status().name();
            command.dueEffect(now, today);
            commands.update(command, now, loaded);
            commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before, command.status().name(), ActorType.SYSTEM, actor, today, now,
                    command.id(), WorkContext.correlationId().orElseGet(ids::next));
            // The day has come: the versions are in force, so a withdrawal still awaiting its countersign lapses (user answer Q2 of 2026-10-30).
            if (command.countersignNewLegalEntityId() != null) {
                countersignRequests.lapse(command, countersigns.lockOfCommand(command.id()), ActorType.SYSTEM, actor, now, today, WorkContext.correlationId().orElseGet(ids::next));
            }
            // As at the entry: a separation's envelope names the entity of the last day employed, any other command the entity of its effective day.
            LocalDate entityDay = command.kind() == PersonnelCommandKind.SEPARATE ? command.effectiveDate().minusDays(1) : command.effectiveDate();
            LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), entityDay).orElse(null);
            command.recordEffect(assignmentBefore(command), null, legalEntity);
            if (command.hasPendingEvents()) {
                events.publish(command);
                commands.saveEventSequence(command.employeeId(), command.eventSequenceAfterSave());
            }
        }
    }

    /** The versions written at entry are voided like those of a withdrawal; the employee stays employed and the separation waits for a release. */
    private void holdOnDueDay(PersonnelCommand command, UUID actor, Instant now, LocalDate today) {
        long loaded = command.version();
        String before = command.status().name();
        command.holdOnDueDay();
        for (Map.Entry<VersionTable, VersionPlan> plan : timelines.forWithdrawal(command, commands.load(command.employeeId())).entrySet()) {
            commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
        }
        commands.update(command, now, loaded);
        UUID correlation = WorkContext.correlationId().orElseGet(ids::next);
        commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before, command.status().name(), ActorType.SYSTEM, actor, today, now, command.id(), correlation);
        SeparationHold hold = SeparationHold.of(new SeparationHoldId(ids.next()), command);
        commands.insertHold(hold, now);
        commands.insertTransition(HOLD, hold.id().value(), hold.version(), null, hold.status().name(), ActorType.SYSTEM, actor, today, now, command.id(), correlation);
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), command.effectiveDate().minusDays(1)).orElse(null);
        command.recordHeld(hold.id(), legalEntity);
        events.publish(command);
        commands.saveEventSequence(command.employeeId(), command.eventSequenceAfterSave());
    }

    /** Only a transfer reads the assignment of the day before; its versions were written at the entry, so the old row still ends where the new one starts. */
    private @Nullable VersionRow assignmentBefore(PersonnelCommand command) {
        if (command.kind() != PersonnelCommandKind.TRANSFER || command.targetAssignmentId() == null) {
            return null;
        }
        return commands.load(command.employeeId()).currentAssignmentRows(command.targetAssignmentId()).stream().filter(row -> row.covers(command.effectiveDate().minusDays(1))).findFirst().orElse(null);
    }
}
