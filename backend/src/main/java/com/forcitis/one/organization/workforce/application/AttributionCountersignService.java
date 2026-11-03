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
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.ApproveAttributionCountersign;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.AttributionCountersigns;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.RejectAttributionCountersign;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRules;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionTable;
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
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The finance countersign of a change of the cost legal entity (ADR-0073 item 3.6, design note of #878 sections 4 and 6). Requests of a personnel command: one transaction holds the employee row
 * locked, the decision, its transition and audit entry and, when it completes the required set, the command's versions or their taking back. The last approval of a
 * waiting command judges the command again; one that no longer holds is recorded as rejected with the reason and the command is withdrawn ("不满足即拒签"). Requests of an org-structure command are decided by OrgCountersigns, which holds the tree lock and the unit row instead.
 */
@Component
class AttributionCountersignService implements AttributionCountersigns {
    static final String PERMISSION = "organization.workforce:legal-entity-attribution:approve";
    private static final String COMMAND = "PersonnelCommand";

    private final AttributionCountersignRepository countersigns;
    private final PersonnelCommandRepository commands;
    private final CountersignRequests requests;
    private final CountersignBases bases;
    private final PersonnelPlans timelines;
    private final EffectPublisher effects;
    private final OwnAttributionGuard ownGuard;
    private final OrgMergeProgress mergeProgress;
    private final CountersignDeciders deciders;
    private final OrgCountersigns orgCountersigns;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    AttributionCountersignService(AttributionCountersignRepository countersigns, PersonnelCommandRepository commands, CountersignRequests requests, CountersignBases bases,
                                  PersonnelPlans timelines, EffectPublisher effects, OwnAttributionGuard ownGuard, OrgMergeProgress mergeProgress, CountersignDeciders deciders, OrgCountersigns orgCountersigns, AuditLog audit,
                                  CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.countersigns = countersigns;
        this.commands = commands;
        this.requests = requests;
        this.bases = bases;
        this.timelines = timelines;
        this.effects = effects;
        this.ownGuard = ownGuard;
        this.mergeProgress = mergeProgress;
        this.deciders = deciders;
        this.orgCountersigns = orgCountersigns;
        this.audit = audit;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public void approve(ApproveAttributionCountersign request) {
        decide(request.attributionCountersignId(), request.reason(), true);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public void reject(RejectAttributionCountersign request) {
        decide(request.attributionCountersignId(), request.reason(), false);
    }

    private void decide(AttributionCountersignId id, @Nullable String reason, boolean approve) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        UUID correlation = WorkContext.correlationId().orElseGet(ids::next);
        AttributionCountersign found = countersigns.find(id).orElseThrow(AttributionCountersignService::notFound);
        if (found.isOrg()) {
            orgCountersigns.decide(id, reason, approve);
            return;
        }
        EmployeeId employee = found.employeeId();
        List<OrgStructureCommandId> holding = mergeProgress.lockHolding(employee);
        commands.lockEmployee(employee);
        AttributionCountersign row = countersigns.find(id).orElseThrow(AttributionCountersignService::notFound);
        PersonnelCommand command = commands.find(row.commandId()).orElseThrow(AttributionCountersignService::notFound);
        requireDecidable(row, command, today);
        requireDecider(row, command, by, today);
        long loaded = row.version();
        if (!approve) {
            row.reject(by, reason, now);
            requests.rejected(row, command, loaded, by, now, today, correlation);
            afterRejection(row, command, by, now, today, correlation);
            return;
        }
        List<AttributionCountersign> siblings = countersigns.ofCommand(command.id()).stream()
                .filter(r -> r.requestKind() == row.requestKind() && r.requestSequenceNumber() == row.requestSequenceNumber()).toList();
        boolean last = CountersignRequests.required(command).stream().allMatch(entity -> entity.equals(row.legalEntityId())
                || siblings.stream().anyMatch(r -> r.legalEntityId().equals(entity) && r.status() == CountersignStatus.APPROVED));
        PersonnelHistory history = commands.load(command.employeeId());
        String failure = last && row.requestKind() == CountersignRequestKind.CHANGE ? recheck(command, history) : null;
        if (failure != null) {
            row.reject(by, failure, now);
            requests.rejected(row, command, loaded, by, now, today, correlation);
            afterRejection(row, command, by, now, today, correlation);
            return;
        }
        row.approve(by, reason, now);
        AuditEntryId entry = requests.approved(row, command, loaded, by, now, today, correlation);
        if (last) {
            if (row.requestKind() == CountersignRequestKind.CHANGE) {
                complete(command, history, entry, by, now, today, correlation);
                mergeProgress.recount(holding);
            } else {
                completeWithdrawal(command, history, row, now, today, correlation);
            }
        }
    }

    /** A rejection withdraws a waiting command; the rejection of a withdrawal request leaves the command standing. The other open requests of the set close. */
    private void afterRejection(AttributionCountersign row, PersonnelCommand command, UserId by, Instant now, LocalDate today, UUID correlation) {
        List<AttributionCountersign> rest = countersigns.ofCommand(command.id()).stream()
                .filter(r -> r.requestKind() == row.requestKind() && (row.requestKind() == CountersignRequestKind.CHANGE || r.requestSequenceNumber() == row.requestSequenceNumber()))
                .toList();
        requests.lapse(command, rest, ActorType.USER, by.value(), now, today, correlation);
        if (row.requestKind() == CountersignRequestKind.CHANGE) {
            withdrawAwaiting(command, by, row.decisionReason(), now, today, correlation);
        }
    }

    private void withdrawAwaiting(PersonnelCommand command, UserId by, @Nullable String reason, Instant now, LocalDate today, UUID correlation) {
        long loaded = command.version();
        CommandStatus before = command.status();
        command.withdraw(by, reason, now);
        commands.update(command, now, loaded);
        commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation);
    }

    /** Every required request is approved: the command's versions are written at its original day, in this transaction, and a command that is due takes effect. */
    private void complete(PersonnelCommand command, PersonnelHistory history, AuditEntryId approval, UserId by, Instant now, LocalDate today, UUID correlation) {
        long loaded = command.version();
        CommandStatus before = command.status();
        command.completeCountersign(now, today);
        commands.update(command, now, loaded);
        commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation);
        Map<VersionTable, VersionPlan> plans = timelines.forEntry(command, history, approval);
        PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
        for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
            commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
        }
        // A correction is effective at once whatever its day, and no due-date step looks at an effective command: its events leave here or never.
        if (command.status() == CommandStatus.EFFECTIVE) {
            effects.publish(command, history, today);
        }
    }

    /** Every finance head approved the withdrawal: the command is withdrawn by whoever asked and its versions are taken out again. */
    private void completeWithdrawal(PersonnelCommand command, PersonnelHistory history, AttributionCountersign row, Instant now, LocalDate today, UUID correlation) {
        long loaded = command.version();
        CommandStatus before = command.status();
        command.withdraw(row.requestedBy(), Objects.requireNonNull(row.requestReason(), "a withdrawal request carries its reason"), now);
        Map<VersionTable, VersionPlan> plans = timelines.forWithdrawal(command, history);
        PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
        for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
            commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
        }
        commands.update(command, now, loaded);
        commands.insertTransition(COMMAND, command.id().value(), loaded + 1, before.name(), command.status().name(), ActorType.USER, row.requestedBy().value(), today, now,
                command.id(), correlation);
        audit.record(new AuditEntry("organization.workforce.personnel-command.withdrawn", row.requestedBy(), null,
                AuditSubject.of(AuditSubjectKind.EMPLOYEE, command.employeeId().value()), "command " + command.id() + " " + command.kind() + "; countersigned withdrawal"));
    }

    /** The command is judged again as when it was entered (ADR-0073 item 3.6): the cross-row rules, and the cost legal entity still the one the countersign was asked about. */
    private @Nullable String recheck(PersonnelCommand command, PersonnelHistory history) {
        try {
            PersonnelCommandRules.check(command, history);
        } catch (DomainException refused) {
            return abbreviated(refused.getMessage());
        }
        boolean unchanged = bases.of(command, history).filter(basis -> basis.previous().equals(command.countersignPreviousLegalEntityId())
                && basis.next().equals(command.countersignNewLegalEntityId())).isPresent();
        return unchanged ? null : "the cost legal entity of the employee on " + command.effectiveDate() + " is no longer the one the countersign was asked about";
    }

    private static String abbreviated(String reason) {
        return reason.length() <= AttributionCountersign.MAX_REASON_LENGTH ? reason : reason.substring(0, AttributionCountersign.MAX_REASON_LENGTH);
    }

    private void requireDecidable(AttributionCountersign row, PersonnelCommand command, LocalDate today) {
        boolean stateHolds = row.requestKind() == CountersignRequestKind.CHANGE ? command.status() == CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN
                : command.status() == CommandStatus.PENDING && command.effectiveDate().isAfter(today);
        if (!row.isOpen() || !stateHolds) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN, ErrorKind.CONFLICT, "the countersign request is not open to a decision");
        }
    }

    /** The decider is the one who may decide the row (see {@link CountersignDeciders}) and is not the employee whose entry changes. */
    private void requireDecider(AttributionCountersign row, PersonnelCommand command, UserId by, LocalDate today) {
        deciders.requireNotParty(row, by);
        ownGuard.requireNotOwn(AttributionSubject.of(command.employeeId()), by);
        deciders.requireHead(row, by, today);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND, ErrorKind.NOT_FOUND, "no such countersign request");
    }
}
