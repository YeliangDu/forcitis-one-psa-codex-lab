package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.EmployeeLegalEntityChange;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The finance countersign of an org-structure command that changes people's cost legal entity (ADR-0073 item 3.6, the org-structure commands design note section 5): the request set at entry, the
 * decisions, the last approval that judges the basis again and writes the command's versions, and the withdrawal of a command that was countersigned. Every method joins the
 * caller's transaction; the use cases take the tree lock first, then the unit root (and the employee roots only to publish).
 */
@Component
class OrgCountersigns {
    private final OrgStructureCommandRepository commands;
    private final PersonnelCommandRepository personnel;
    private final AttributionCountersignRepository countersigns;
    private final CountersignRequests requests;
    private final CountersignDeciders deciders;
    private final OrgCountersignBases bases;
    private final OrgStructureRules rules;
    private final OrgCommandWrites writes;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OrgCountersigns(OrgStructureCommandRepository commands, PersonnelCommandRepository personnel, AttributionCountersignRepository countersigns, CountersignRequests requests,
                    CountersignDeciders deciders, OrgCountersignBases bases, OrgStructureRules rules, OrgCommandWrites writes, AuditLog audit, DomainEvents events, CurrentUser currentUser, IdGenerator ids,
                    Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.personnel = personnel;
        this.countersigns = countersigns;
        this.requests = requests;
        this.deciders = deciders;
        this.bases = bases;
        this.rules = rules;
        this.writes = writes;
        this.audit = audit;
        this.events = events;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    /** The command waits: one CHANGE request per legal entity of the basis' before and after. */
    void openChange(OrgStructureCommand command, List<OrgCountersignBasis> basis, UserId by, Instant now, LocalDate today, UUID correlation) {
        Set<LegalEntityId> entities = new LinkedHashSet<>();
        for (OrgCountersignBasis row : basis) {
            entities.add(row.previous());
            entities.add(row.next());
        }
        requests.open(command, entities, CountersignRequestKind.CHANGE, null, List.of(), by, now, today, correlation);
    }

    /** The drafter withdraws a command that waits: no head decides any more, so the open requests close. */
    void lapseAwaiting(OrgStructureCommand command, UserId by, Instant now, LocalDate today, UUID correlation) {
        requests.lapse(CountersignRequests.Parent.of(command), countersigns.ofOrgCommand(command.id()), ActorType.USER, by.value(), now, today, correlation);
    }

    /**
     * A countersigned command that has not taken effect is withdrawn only when every head agrees: opens the WITHDRAWAL set and answers true. False for a command that never
     * needed the countersign, which its drafter withdraws directly.
     */
    boolean requestWithdrawal(OrgStructureCommand command, @Nullable String reason, UserId by, Instant now, LocalDate today, UUID correlation) {
        List<AttributionCountersign> existing = countersigns.ofOrgCommand(command.id());
        Set<LegalEntityId> entities = new LinkedHashSet<>();
        existing.stream().filter(r -> r.requestKind() == CountersignRequestKind.CHANGE).forEach(r -> entities.add(r.legalEntityId()));
        if (command.status() != OrgCommandStatus.PENDING || entities.isEmpty()) {
            return false;
        }
        if (!command.effectiveDate().isAfter(today)) {
            throw notAllowed("the effective date has come; undo the command with a new countersigned command");
        }
        if (existing.stream().anyMatch(r -> r.requestKind() == CountersignRequestKind.WITHDRAWAL && r.isOpen())) {
            throw notAllowed("a withdrawal request for the command is already open");
        }
        requests.open(command, entities, CountersignRequestKind.WITHDRAWAL, reason, existing, by, now, today, correlation);
        return true;
    }

    /** A head approves or rejects one request; the last approval judges the command again and writes it, or withdraws it when it no longer holds. */
    void decide(AttributionCountersignId id, @Nullable String reason, boolean approve) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        UUID correlation = WorkContext.correlationId().orElseGet(ids::next);
        AttributionCountersign probe = countersigns.find(id).orElseThrow(OrgCountersigns::notFound);
        commands.lockOrgTree();
        OrgStructureCommand found = commands.find(Objects.requireNonNull(probe.orgCommandId())).orElseThrow(OrgCountersigns::notFound);
        commands.lockOrgUnit(Objects.requireNonNull(found.targetOrgUnitId()));
        AttributionCountersign row = countersigns.find(id).orElseThrow(OrgCountersigns::notFound);
        OrgStructureCommand command = commands.find(found.id()).orElseThrow(OrgCountersigns::notFound);
        requireDecidable(row, command, today);
        deciders.require(row, by, today);
        if (approve) {
            bases.requireUserUnaffected(command, by);
        }
        CountersignRequests.Parent parent = CountersignRequests.Parent.of(command);
        long loaded = row.version();
        if (!approve) {
            row.reject(by, reason, now);
            requests.rejected(row, parent, loaded, by, now, today, correlation);
            afterRejection(row, command, by, now, today, correlation);
            return;
        }
        List<AttributionCountersign> set = countersigns.ofOrgCommand(command.id()).stream()
                .filter(r -> r.requestKind() == row.requestKind() && r.requestSequenceNumber() == row.requestSequenceNumber()).toList();
        boolean last = set.stream().allMatch(r -> r.id().equals(row.id()) || r.status() == CountersignStatus.APPROVED);
        String failure = last && row.requestKind() == CountersignRequestKind.CHANGE ? recheck(command) : null;
        if (failure != null) {
            row.reject(by, failure, now);
            requests.rejected(row, parent, loaded, by, now, today, correlation);
            afterRejection(row, command, by, now, today, correlation);
            return;
        }
        row.approve(by, reason, now);
        AuditEntryId entry = requests.approved(row, parent, loaded, by, now, today, correlation);
        if (last) {
            if (row.requestKind() == CountersignRequestKind.CHANGE) {
                complete(command, entry, by, now, today, correlation);
            } else {
                completeWithdrawal(command, row, now, today, correlation);
            }
        }
    }

    /** A rejection withdraws a waiting command; the rejection of a withdrawal request leaves the command standing. The other open requests of the set close. */
    private void afterRejection(AttributionCountersign row, OrgStructureCommand command, UserId by, Instant now, LocalDate today, UUID correlation) {
        List<AttributionCountersign> rest = countersigns.ofOrgCommand(command.id()).stream()
                .filter(r -> r.requestKind() == row.requestKind() && (row.requestKind() == CountersignRequestKind.CHANGE || r.requestSequenceNumber() == row.requestSequenceNumber()))
                .toList();
        requests.lapse(CountersignRequests.Parent.of(command), rest, ActorType.USER, by.value(), now, today, correlation);
        if (row.requestKind() == CountersignRequestKind.CHANGE) {
            long loaded = command.version();
            OrgCommandStatus before = command.status();
            command.withdraw(by, row.decisionReason(), now);
            commands.update(command, now, loaded);
            commands.insertTransition(command.id(), loaded + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, correlation);
        }
    }

    /** Every required request is approved: the command's versions are written at its day, in this transaction, and a command that is due takes effect. */
    private void complete(OrgStructureCommand command, AuditEntryId approval, UserId by, Instant now, LocalDate today, UUID correlation) {
        long loaded = command.version();
        OrgCommandStatus before = command.status();
        command.completeCountersign(now, today);
        writes.applyAfterCountersign(command, approval, now);
        commands.update(command, now, loaded);
        commands.insertTransition(command.id(), loaded + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, correlation);
        if (command.status() == OrgCommandStatus.EFFECTIVE) {
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

    /** Every head approved the withdrawal: the command is withdrawn by whoever asked and its versions are taken out again. */
    private void completeWithdrawal(OrgStructureCommand command, AttributionCountersign row, Instant now, LocalDate today, UUID correlation) {
        long loaded = command.version();
        OrgCommandStatus before = command.status();
        command.withdraw(row.requestedBy(), Objects.requireNonNull(row.requestReason(), "a withdrawal request carries its reason"), now);
        AuditEntryId entry = audit.record(new AuditEntry("organization.workforce.org-structure-command.withdrawn", row.requestedBy(), null,
                CountersignRequests.Parent.of(command).subject(), "command " + command.id() + " " + command.kind() + "; countersigned withdrawal"));
        writes.takeBack(command, entry, now);
        commands.update(command, now, loaded);
        commands.insertTransition(command.id(), loaded + 1, before.name(), command.status().name(), ActorType.USER, row.requestedBy().value(), today, now, correlation);
    }

    /** The command is judged again as when it was entered: the structural rules still hold on the timeline as it stands, and the basis recomputed equals the basis stored (the same people, the same entity before and after). */
    private @Nullable String recheck(OrgStructureCommand command) {
        try {
            rules.checkStillHolds(command, writes.planNow(command, new AuditEntryId(ids.next())));
        } catch (DomainException refused) {
            return refused.getMessage();
        }
        List<OrgCountersignBasis> now;
        try {
            now = bases.of(command);
        } catch (DomainException refused) {
            return refused.getMessage();
        }
        boolean unchanged = Set.copyOf(now).equals(Set.copyOf(commands.basisOf(command.id())));
        return unchanged ? null : "the employees whose cost legal entity the command changes, or the entities before and after, are no longer the ones the countersign was asked about";
    }

    private static void requireDecidable(AttributionCountersign row, OrgStructureCommand command, LocalDate today) {
        boolean stateHolds = row.requestKind() == CountersignRequestKind.CHANGE ? command.status() == OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN
                : command.status() == OrgCommandStatus.PENDING && command.effectiveDate().isAfter(today);
        if (!row.isOpen() || !stateHolds) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN, ErrorKind.CONFLICT, "the countersign request is not open to a decision");
        }
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND, ErrorKind.NOT_FOUND, "no such countersign request");
    }

    private static DomainException notAllowed(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, message);
    }
}
