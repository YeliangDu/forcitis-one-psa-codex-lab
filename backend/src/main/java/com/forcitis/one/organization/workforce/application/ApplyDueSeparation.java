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
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The due-date step for one released separation (ADR-0068 items 1.8 and 2.3): on its effective date the pending command becomes effective and
 * EmployeeSeparated is published, once. Its versions were written at the release and are not touched; it is never judged or held again. Nothing
 * else changes: a command that is not pending any more (withdrawn meanwhile, or already done) is left as it is.
 */
@SystemActorName("organization.workforce.personnel.command.due")
@Component
public class ApplyDueSeparation {
    private static final String ACTOR = ApplyDueSeparation.class.getAnnotation(SystemActorName.class).value();
    private static final String COMMAND = "PersonnelCommand";

    private final PersonnelCommandRepository commands;
    private final LegalEntityResolutionSources legalEntities;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    ApplyDueSeparation(PersonnelCommandRepository commands, LegalEntityResolutionSources legalEntities, DomainEvents events, IdGenerator ids, Clock clock,
                       BusinessZone zone) {
        this.commands = commands;
        this.legalEntities = legalEntities;
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
            long loaded = command.version();
            String before = command.status().name();
            command.dueEffect(now, today);
            commands.update(command, now, loaded);
            UUID actor = SystemActors.idOf(ACTOR);
            commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before, command.status().name(), ActorType.SYSTEM, actor, today, now,
                    command.id(), WorkContext.correlationId().orElseGet(ids::next));
            LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), command.effectiveDate().minusDays(1)).orElse(null);
            command.recordEffect(null, legalEntity);
            events.publish(command);
            commands.saveEventSequence(command.employeeId(), command.eventSequenceAfterSave());
        }
    }
}
