package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.EmployeeLegalEntityChange;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The day of one pending org-structure command that is not a merge has come (ADR-0068 item 1.8): it turns effective, a withdrawal request still open lapses, and the events are
 * published. Its versions are in force since the entry or the last countersign, so nothing is judged; it writes the command's state, lapses an open withdrawal set and advances the event sequence of each basis employee. The locks are those of
 * the approval: tree, unit root, then the countersign rows and the employee roots, so the countersign deadline job and a decision cannot interleave with it on shared rows.
 */
@SystemActorName("organization.workforce.org.structure.command.due")
@Service
class ApplyDueOrgCommand {
    private static final String ACTOR = ApplyDueOrgCommand.class.getAnnotation(SystemActorName.class).value();

    private final OrgStructureCommandRepository commands;
    private final PersonnelCommandRepository personnel;
    private final AttributionCountersignRepository countersigns;
    private final CountersignRequests requests;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    ApplyDueOrgCommand(OrgStructureCommandRepository commands, PersonnelCommandRepository personnel, AttributionCountersignRepository countersigns, CountersignRequests requests,
                       DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.personnel = personnel;
        this.countersigns = countersigns;
        this.requests = requests;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void execute(OrgStructureCommandId id) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        try (WorkContext.Scope scope = WorkContext.enter(Uuids.timeOrdered(clock), null, ACTOR)) {
            commands.lockOrgTree();
            OrgStructureCommand found = commands.find(id).orElse(null);
            if (found == null) {
                return;
            }
            if (found.targetOrgUnitId() != null) {
                commands.lockOrgUnit(found.targetOrgUnitId());
            }
            OrgStructureCommand command = commands.find(id).orElseThrow();
            if (command.status() != OrgCommandStatus.PENDING || command.effectiveDate().isAfter(today)) {
                return;
            }
            UUID actor = SystemActors.idOf(ACTOR);
            UUID correlation = WorkContext.correlationId().orElseGet(ids::next);
            long loaded = command.version();
            String before = command.status().name();
            command.dueEffect(now);
            commands.update(command, now, loaded);
            commands.insertTransition(command.id(), loaded + 1, before, command.status().name(), ActorType.SYSTEM, actor, today, now, correlation);
            // The day has come: a withdrawal request still open can no longer be decided (requireDecidable), so it lapses, as a personnel command's does.
            if (command.targetOrgUnitId() != null) {
                requests.lapse(CountersignRequests.Parent.of(command), countersigns.lockOfOrgCommand(command.id()), ActorType.SYSTEM, actor, now, today, correlation);
            }
            command.recordEffect();
            events.publish(command);
            for (OrgCountersignBasis basis : commands.basisOf(command.id())) {
                commands.lockEmployee(basis.employeeId());
                EmployeeLegalEntityChange change = EmployeeLegalEntityChange.of(command.id(), command.effectiveDate(), basis, personnel.eventSequence(basis.employeeId()));
                events.publish(change);
                personnel.saveEventSequence(basis.employeeId(), change.eventSequenceAfterSave());
            }
        }
    }
}
