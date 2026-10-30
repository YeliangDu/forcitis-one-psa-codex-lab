package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
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
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering and withdrawing personnel commands (ADR-0068 item 1). One transaction holds the employee row locked, the cross-row rules, the command,
 * the version rows (W2 design sections 0 and 2), the transitions, the audit entry and, when the command takes effect, the event. The permission is
 * checked before anything about the content is read. Two methods, like the other adapters of an api interface in this module.
 */
@Component
class PersonnelCommandService implements PersonnelCommands {
    static final String PERMISSION = "organization.workforce:personnel-command:create";
    private static final String COMMAND = "PersonnelCommand";
    private static final String HOLD = "SeparationHold";

    private final PersonnelCommandRepository commands;
    private final PersonnelPlans timelines;
    private final SeparationHoldCheck holdCheck;
    private final LegalEntityResolutionSources legalEntities;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PersonnelCommandService(PersonnelCommandRepository commands, PersonnelPlans timelines, SeparationHoldCheck holdCheck, LegalEntityResolutionSources legalEntities,
                            AuditLog audit, DomainEvents events, CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.timelines = timelines;
        this.holdCheck = holdCheck;
        this.legalEntities = legalEntities;
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
        EmployeeId employee = request.employeeId();
        if (!commands.lockEmployee(employee)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "the employee does not exist");
        }
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(ids.next()), request, by, now, today, commands.eventSequence(employee));
        requireTargetsExist(command);
        PersonnelHistory history = commands.load(employee);
        PersonnelCommandRules.check(command, history);
        // B6 seam (ADR-0068 item 2): a held separation must also leave out its separated versions now and void the future one when released late;
        // withdraw() below reverts versions only for a pending command, so B6 handles both ends here.
        if (command.kind() == PersonnelCommandKind.SEPARATE && holdCheck.holds(command)) {
            command.holdAtEntry();
        }
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.personnel-command.entered", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()),
                "command " + command.id() + " " + command.kind() + " effective " + command.effectiveDate()));
        Map<VersionTable, VersionPlan> plans = timelines.forEntry(command, history, auditEntry);
        PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
        commands.insert(command);
        commands.insertTransition(COMMAND, command.id().value(), command.version(), null, command.status().name(), by, today, now, command.id(), correlation());
        for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
            commands.insertVersions(employee, plan.getKey(), plan.getValue(), command.id(), now);
        }
        if (command.status() == CommandStatus.EFFECTIVE && !(command.kind() == PersonnelCommandKind.CORRECTION && command.effectiveDate().isAfter(today))) {
            VersionRow assignmentBefore = history.currentAssignmentRows().stream().filter(r -> r.covers(command.effectiveDate().minusDays(1))).findFirst().orElse(null);
            // A separation's envelope names the entity of the last day the employee was employed: on the effective day itself the assignment has just ended.
            LocalDate entityDay = command.kind() == PersonnelCommandKind.SEPARATE ? command.effectiveDate().minusDays(1) : command.effectiveDate();
            command.recordEffect(assignmentBefore, legalEntityOn(employee, entityDay));
            if (command.hasPendingEvents()) {
                events.publish(command);
                commands.saveEventSequence(employee, command.eventSequenceAfterSave());
            }
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
        commands.lockEmployee(found.employeeId());
        PersonnelCommand command = commands.find(request.personnelCommandId()).orElseThrow(PersonnelCommandService::notFound);
        long loadedVersion = command.version();
        CommandStatus before = command.status();
        command.withdraw(by, request.reason(), now);
        PersonnelHistory history = commands.load(command.employeeId());
        if (before == CommandStatus.PENDING) {
            Map<VersionTable, VersionPlan> plans = timelines.forWithdrawal(command, history);
            PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
            for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
                commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
            }
        }
        commands.update(command, loadedVersion);
        commands.insertTransition(COMMAND, command.id().value(), loadedVersion + 1, before.name(), command.status().name(), by, today, now, command.id(), correlation());
        commands.holdOf(command.id()).ifPresent(hold -> withdrawHold(hold, command, by, today, now));
        audit.record(new AuditEntry("organization.workforce.personnel-command.withdrawn", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, command.employeeId().value()),
                "command " + command.id() + " " + command.kind() + "; reason: " + command.withdrawalReason().replaceAll("\\p{Cntrl}", " ")));
    }

    private void withdrawHold(SeparationHold hold, PersonnelCommand command, UserId by, LocalDate today, Instant now) {
        long loadedVersion = hold.version();
        String before = hold.status().name();
        hold.withdraw();
        commands.updateHold(hold, now, loadedVersion);
        commands.insertTransition(HOLD, hold.id().value(), hold.version(), before, hold.status().name(), by, today, now, command.id(), correlation());
    }

    /** The envelope carries the employee's legal entity on the effective date (contract question Q6); none resolvable means the envelope has none. */
    private @Nullable LegalEntityId legalEntityOn(EmployeeId employee, LocalDate day) {
        return LegalEntityResolution.resolve(legalEntities, employee, day).orElse(null);
    }

    private void requireTargetsExist(PersonnelCommand command) {
        if (command.targetOrgUnitId() != null && !commands.orgUnitExists(command.targetOrgUnitId())
                || command.targetPositionId() != null && !commands.positionExists(command.targetPositionId())
                || command.targetReportsToEmployeeId() != null && !commands.employeeExists(command.targetReportsToEmployeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE,
                    "a department, position or manager the command names does not exist");
        }
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such personnel command");
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }
}
