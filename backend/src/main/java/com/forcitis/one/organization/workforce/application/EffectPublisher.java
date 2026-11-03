package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import java.time.LocalDate;
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

    EffectPublisher(PersonnelCommandRepository commands, LegalEntityResolutionSources legalEntities, DomainEvents events) {
        this.commands = commands;
        this.legalEntities = legalEntities;
        this.events = events;
    }

    /** @param historyBefore the employee's history as it was before the command's versions were written (the transfer reads the assignment of the day before) */
    void publish(PersonnelCommand command, PersonnelHistory historyBefore) {
        VersionRow assignmentBefore = historyBefore.currentAssignmentRows().stream().filter(r -> r.covers(command.effectiveDate().minusDays(1))).findFirst().orElse(null);
        // A separation's envelope names the entity of the last day the employee was employed: on the effective day itself the assignment has just ended.
        LocalDate entityDay = command.kind() == PersonnelCommandKind.SEPARATE ? command.effectiveDate().minusDays(1) : command.effectiveDate();
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, command.employeeId(), entityDay).orElse(null);
        command.recordEffect(assignmentBefore, legalEntity);
        if (command.hasPendingEvents()) {
            events.publish(command);
            commands.saveEventSequence(command.employeeId(), command.eventSequenceAfterSave());
        }
    }
}
