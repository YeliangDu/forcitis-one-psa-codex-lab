package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One expiry shares the employee lock and event stream of personnel commands, without inserting a system command. */
@Component
@SystemActorName("organization.workforce.personnel.command.due")
public class ExpireActingDuty {
    private static final String ACTOR = ExpireActingDuty.class.getAnnotation(SystemActorName.class).value();
    private final ActingDutyRepository duties;
    private final PersonnelCommandRepository commands;
    private final LegalEntityResolutionSources legalEntities;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    ExpireActingDuty(ActingDutyRepository duties, PersonnelCommandRepository commands, LegalEntityResolutionSources legalEntities,
                    DomainEvents events, Clock clock, BusinessZone zone) {
        this.duties = duties;
        this.commands = commands;
        this.legalEntities = legalEntities;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void execute(ActingDutyId id) {
        Instant now = clock.instant();
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, ACTOR)) {
            ActingDuty selected = duties.find(id).orElse(null);
            if (selected == null) {
                return;
            }
            duties.lockEmployee(selected.employeeId());
            ActingDuty duty = duties.find(id).orElseThrow();
            if (duty.endedOn() != null || duty.endsBefore().isAfter(zone.dateOf(now))) {
                return;
            }
            duties.updateEnd(duty, duty.endsBefore(), ActingDutyEndCause.EXPIRED, now);
            LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, duty.employeeId(), duty.endsBefore()).orElse(null);
            var source = commands.find(duty.sourcePersonnelCommandId()).orElseThrow();
            source.recordActingExpiry(duty, legalEntity);
            events.publish(source);
            duties.saveEventSequence(duty.employeeId(), source.eventSequenceAfterSave());
        }
    }
}
