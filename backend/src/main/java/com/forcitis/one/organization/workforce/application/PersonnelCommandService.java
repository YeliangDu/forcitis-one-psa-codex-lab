package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRules;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
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
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering and withdrawing personnel commands (ADR-0068 item 1). One transaction holds the employee row locked, the cross-row rules, the command,
 * the version rows (W2 design sections 0 and 2), the transitions, the audit entry and, when the command takes effect, the event. The permission is
 * checked before anything about the content is read. A start of unpaid leave takes the separation lock and passes the administrator floor. Two methods, like the other adapters of an api interface in this module.
 */
@Component
class PersonnelCommandService implements PersonnelCommands {
    static final String PERMISSION = "organization.workforce:personnel-command:create";
    private static final String COMMAND = "PersonnelCommand";
    private static final String HOLD = "SeparationHold";

    private final PersonnelCommandRepository commands;
    private final NewEmployees newEmployees;
    private final PersonnelPlans timelines;
    private final SeparationHoldCheck holdCheck;
    private final UnpaidLeaveStartFloor leaveFloor;
    private final LegalEntityResolutionSources legalEntities;
    private final CountersignBases bases;
    private final CountersignRequests requests;
    private final AttributionCountersignRepository countersigns;
    private final OwnAttributionGuard ownGuard;
    private final EffectPublisher effects;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PersonnelCommandService(PersonnelCommandRepository commands, NewEmployees newEmployees, PersonnelPlans timelines, SeparationHoldCheck holdCheck, UnpaidLeaveStartFloor leaveFloor,
                            LegalEntityResolutionSources legalEntities, CountersignBases bases, CountersignRequests requests,
                            AttributionCountersignRepository countersigns, OwnAttributionGuard ownGuard, EffectPublisher effects, AuditLog audit, DomainEvents events,
                            CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.newEmployees = newEmployees;
        this.timelines = timelines;
        this.holdCheck = holdCheck;
        this.leaveFloor = leaveFloor;
        this.legalEntities = legalEntities;
        this.bases = bases;
        this.requests = requests;
        this.countersigns = countersigns;
        this.ownGuard = ownGuard;
        this.effects = effects;
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
    public PersonnelCommandId enter(EnterPersonnelCommand request) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        EmployeeId employee;
        if (request.commandKind() == PersonnelCommandKind.HIRE) {
            employee = newEmployees.create(request, today, now);
        } else {
            employee = existingEmployee(request);
            if (request.commandKind() == PersonnelCommandKind.SEPARATE || request.commandKind() == PersonnelCommandKind.UNPAID_LEAVE_START) {
                commands.lockSeparationDecisions();
            }
            if (!commands.lockEmployee(employee)) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "the employee does not exist");
            }
        }
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(ids.next()), employee, request, by, now, today, commands.eventSequence(employee));
        requireTargetsExist(command);
        PersonnelHistory history = commands.load(employee);
        PersonnelCommandRules.check(command, history);
        PersonnelCommandRules.requireNoAwaitingOnOrBefore(command, history);
        bases.of(command, history).ifPresent(basis -> {
            // Nobody changes their own legal entity (ADR-0073 item 3.5, ADR-0027 item 2.2): the drafter is refused when it is theirs that would change.
            ownGuard.requireNotOwn(AttributionSubject.of(employee), by);
            command.requireCountersign(basis.previous(), basis.next());
        });
        boolean awaiting = command.status() == CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN;
        SeparationReview.@Nullable Verdict leaveVerdict = command.kind() == PersonnelCommandKind.UNPAID_LEAVE_START ? leaveFloor.check(command) : null;
        boolean held = command.kind() == PersonnelCommandKind.SEPARATE && holdCheck.holds(command);
        if (held) {
            command.holdAtEntry();
        }
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.personnel-command.entered", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()),
                "command " + command.id() + " " + command.kind() + " effective " + command.effectiveDate()));
        Map<VersionTable, VersionPlan> plans = held || awaiting ? Map.of() : timelines.forEntry(command, history, auditEntry);
        PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
        commands.insert(command);
        commands.insertTransition(COMMAND, command.id().value(), command.version(), null, command.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation());
        for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
            commands.insertVersions(employee, plan.getKey(), plan.getValue(), command.id(), now);
        }
        if (held) {
            SeparationHold hold = SeparationHold.of(new SeparationHoldId(ids.next()), command);
            commands.insertHold(hold, now);
            commands.insertTransition(HOLD, hold.id().value(), hold.version(), null, hold.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation());
            command.recordHeld(hold.id(), legalEntityOn(employee, command.effectiveDate().minusDays(1)));
            events.publish(command);
            commands.saveEventSequence(employee, command.eventSequenceAfterSave());
        } else if (awaiting) {
            requests.open(command, CountersignRequestKind.CHANGE, null, List.of(), by, now, today, correlation());
        } else if (command.status() == CommandStatus.EFFECTIVE && !(command.kind() == PersonnelCommandKind.CORRECTION && command.effectiveDate().isAfter(today))) {
            effects.publish(command, history);
        }
        if (leaveVerdict != null) {
            leaveFloor.announce(command, leaveVerdict, by);
        }
        return command.id();
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public void withdraw(WithdrawPersonnelCommand request) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        PersonnelCommand found = commands.find(request.personnelCommandId()).orElseThrow(PersonnelCommandService::notFound);
        if (found.kind() == PersonnelCommandKind.SEPARATE) {
            commands.lockSeparationDecisions();
        }
        commands.lockEmployee(found.employeeId());
        PersonnelCommand command = commands.find(request.personnelCommandId()).orElseThrow(PersonnelCommandService::notFound);
        long loadedVersion = command.version();
        CommandStatus before = command.status();
        if (before == CommandStatus.PENDING && command.countersignNewLegalEntityId() != null) {
            requestWithdrawal(command, request.reason(), by, now, today);
            return;
        }
        command.withdraw(by, request.reason(), now);
        if (before == CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN) {
            requests.lapse(command, countersigns.ofCommand(command.id()), ActorType.USER, by.value(), now, today, correlation());
        }
        PersonnelHistory history = commands.load(command.employeeId());
        if (before == CommandStatus.PENDING) {
            Map<VersionTable, VersionPlan> plans = timelines.forWithdrawal(command, history);
            PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
            for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
                commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
            }
        }
        commands.update(command, now, loadedVersion);
        commands.insertTransition(COMMAND, command.id().value(), loadedVersion + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation());
        // A released hold stays released when its still pending command is withdrawn: the release was decided.
        commands.holdOf(command.id()).filter(hold -> hold.status() == HoldStatus.HELD).ifPresent(hold -> withdrawHold(hold, command, by, today, now));
        audit.record(new AuditEntry("organization.workforce.personnel-command.withdrawn", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, command.employeeId().value()),
                "command " + command.id() + " " + command.kind() + "; reason: " + command.withdrawalReason().replaceAll("\\p{Cntrl}", " ")));
    }

    /** The versions of a countersigned command stay until every finance head of its two legal entities approves the withdrawal (ADR-0073 item 3.6). */
    private void requestWithdrawal(PersonnelCommand command, @Nullable String reason, UserId by, Instant now, LocalDate today) {
        command.requireWithdrawalAllowed(reason, today);
        // Nobody undoes the change of their own legal entity either (ADR-0073 item 3.5): the same guard as the entry.
        ownGuard.requireNotOwn(AttributionSubject.of(command.employeeId()), by);
        List<AttributionCountersign> existing = countersigns.ofCommand(command.id());
        if (existing.stream().anyMatch(r -> r.isOpen() && r.requestKind() == CountersignRequestKind.WITHDRAWAL)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, "a withdrawal of this command already awaits the countersign");
        }
        requests.open(command, CountersignRequestKind.WITHDRAWAL, reason, existing, by, now, today, correlation());
    }

    private void withdrawHold(SeparationHold hold, PersonnelCommand command, UserId by, LocalDate today, Instant now) {
        long loadedVersion = hold.version();
        String before = hold.status().name();
        hold.withdraw();
        commands.updateHold(hold, now, loadedVersion);
        commands.insertTransition(HOLD, hold.id().value(), hold.version(), before, hold.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation());
    }

    /** The envelope carries the employee's legal entity on the effective date (contract question Q6); none resolvable means the envelope has none. */
    private @Nullable LegalEntityId legalEntityOn(EmployeeId employee, LocalDate day) {
        return LegalEntityResolution.resolve(legalEntities, employee, day).orElse(null);
    }

    /** Every kind but a hire is about a named employee and carries no profile (the profile of an existing employee is changed by its own use case). */
    private static EmployeeId existingEmployee(EnterPersonnelCommand request) {
        if (request.employeeId() == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "the employee is required");
        }
        if (request.displayName() != null || request.gender() != null || request.email() != null || request.wecomUserId() != null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "only a hire carries a profile");
        }
        return request.employeeId();
    }

    private void requireTargetsExist(PersonnelCommand command) {
        if (command.targetOrgUnitId() != null && !commands.orgUnitExists(command.targetOrgUnitId())
                || command.targetPositionId() != null && !commands.positionExists(command.targetPositionId())
                || command.targetReportsToEmployeeId() != null && !commands.employeeExists(command.targetReportsToEmployeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE,
                    "a department, position or manager the command names does not exist");
        }
        if (command.targetLegalEntityId() != null && !commands.legalEntityIsActive(command.targetLegalEntityId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE,
                    "the legal entity the command names does not exist or is not active");
        }
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such personnel command");
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }
}
