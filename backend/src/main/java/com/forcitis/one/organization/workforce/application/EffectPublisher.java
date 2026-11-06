package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import com.forcitis.one.organization.workforce.domain.ActingDutyRules;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The events of a command that took effect in this transaction, after its versions are written: the entry of a command dated today or earlier, and the last
 * countersign of one that waited. The envelope names the legal entity of the effective day (a separation: of the last day employed).
 */
@Component
class EffectPublisher {
    private final PersonnelCommandRepository commands;
    private final LegalEntityResolutionSources legalEntities;
    private final DomainEvents events;
    private final ActingDutyRepository duties;
    private final IdGenerator ids;
    private final Clock clock;

    EffectPublisher(PersonnelCommandRepository commands, LegalEntityResolutionSources legalEntities, DomainEvents events, ActingDutyRepository duties, IdGenerator ids, Clock clock) {
        this.commands = commands;
        this.legalEntities = legalEntities;
        this.events = events;
        this.duties = duties;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * @param historyBefore the employee's history as it was before the command's versions were written (a transfer reads the assignment of the day before,
     *                      a correction the assignment today, before and after it; ADR-0081 decision 3)
     */
    void publish(PersonnelCommand command, PersonnelHistory historyBefore, LocalDate today) {
        if (command.kind() == PersonnelCommandKind.ACTING_END) {
            changeActingDuty(command, ActingDutyRules.requireTarget(command, duties.find(Objects.requireNonNull(command.targetActingDutyId())).orElse(null)));
            return;
        }
        boolean correction = command.kind() == PersonnelCommandKind.CORRECTION;
        VersionRow assignmentBefore = historyBefore.assignmentOn(correction ? today : command.effectiveDate().minusDays(1));
        VersionRow assignmentAfter = correction ? commands.load(command.employeeId()).assignmentOn(today) : null;
        // A separation's envelope names the entity of the last day the employee was employed: on the effective day itself the assignment has just ended.
        LocalDate entityDay = command.kind() == PersonnelCommandKind.SEPARATE ? command.effectiveDate().minusDays(1) : command.effectiveDate();
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), entityDay).orElse(null);
        if (command.kind() == PersonnelCommandKind.ACTING_START) {
            ActingDuty duty = ActingDuty.start(new ActingDutyId(ids.next()), command, clock.instant());
            duties.insert(duty);
            command.recordActingStarted(duty, legalEntity);
        } else {
            command.recordEffect(assignmentBefore, assignmentAfter, legalEntity, commands.load(command.employeeId()));
        }
        publishRecorded(command);
    }

    boolean actingEndIsClosed(PersonnelCommand command) {
        return command.kind() == PersonnelCommandKind.ACTING_END
                && duties.find(Objects.requireNonNull(command.targetActingDutyId())).orElseThrow().endedOn() != null;
    }

    void changeActingDuty(PersonnelCommand command, ActingDuty loaded) {
        ActingDutyRules.requireTarget(command, loaded);
        if (command.kind() == PersonnelCommandKind.ACTING_EXTEND) {
            duties.updateEndsBefore(loaded, Objects.requireNonNull(command.actingEndsBeforeDate()), clock.instant());
            return;
        }
        ActingDutyRules.requireEnd(command, loaded, java.util.List.of());
        duties.updateEnd(loaded, command.effectiveDate(), ActingDutyEndCause.ENDED_EARLY, clock.instant());
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), command.effectiveDate()).orElse(null);
        command.recordActingEnded(loaded, legalEntity);
        publishRecorded(command);
    }

    private void publishRecorded(PersonnelCommand command) {
        if (command.hasPendingEvents()) {
            events.publish(command);
            commands.saveEventSequence(command.employeeId(), command.eventSequenceAfterSave());
        }
    }
}
