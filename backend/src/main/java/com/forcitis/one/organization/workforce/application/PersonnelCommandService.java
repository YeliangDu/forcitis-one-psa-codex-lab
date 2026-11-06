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
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import com.forcitis.one.organization.workforce.domain.ActingDutyRules;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRules;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.RelationshipRow;
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
import java.util.Objects;
import java.util.Set;
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
    private final ActingDutyRepository duties;
    private final NewEmployees newEmployees;
    private final PersonnelPlans timelines;
    private final SeparationHoldCheck holdCheck;
    private final UnpaidLeaveStartFloor leaveFloor;
    private final LegalEntityResolutionSources legalEntities;
    private final CountersignBases bases;
    private final CountersignRequests requests;
    private final OrgCountersignFreeze freeze;
    private final AttributionCountersignRepository countersigns;
    private final OwnAttributionGuard ownGuard;
    private final OrgMergeProgress mergeProgress;
    private final EffectPublisher effects;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PersonnelCommandService(PersonnelCommandRepository commands, NewEmployees newEmployees, PersonnelPlans timelines, SeparationHoldCheck holdCheck, UnpaidLeaveStartFloor leaveFloor,
                            LegalEntityResolutionSources legalEntities, CountersignBases bases, CountersignRequests requests,
                            OrgCountersignFreeze freeze, AttributionCountersignRepository countersigns, OwnAttributionGuard ownGuard, OrgMergeProgress mergeProgress, EffectPublisher effects, AuditLog audit, DomainEvents events,
                            CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone, ActingDutyRepository duties) {
        this.commands = commands;
        this.duties = duties;
        this.newEmployees = newEmployees;
        this.timelines = timelines;
        this.holdCheck = holdCheck;
        this.leaveFloor = leaveFloor;
        this.legalEntities = legalEntities;
        this.bases = bases;
        this.requests = requests;
        this.freeze = freeze;
        this.countersigns = countersigns;
        this.ownGuard = ownGuard;
        this.mergeProgress = mergeProgress;
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
        return enter(request, currentUser.userId(), null);
    }

    /**
     * The entry of a transfer that an org-structure merge generates for one person: the same rules and the same writes, in the caller's transaction, with
     * the merge on the command and the system actor of the due-date step as recorder. No permission is checked here; the merge's own entry was.
     */
    @Transactional
    public PersonnelCommandId enter(EnterPersonnelCommand request, UserId by, @Nullable OrgStructureCommandId merge) {
        if (merge == null && request.targetOrgUnitId() != null) {
            commands.lockOrgUnitForEntry(request.targetOrgUnitId());
        }
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        EmployeeId employee;
        List<OrgStructureCommandId> holding = List.of();
        if (request.commandKind() == PersonnelCommandKind.HIRE && request.employeeId() == null) {
            employee = newEmployees.create(request, today, now);
        } else {
            employee = existingEmployee(request);
            if (request.commandKind() == PersonnelCommandKind.SEPARATE || request.commandKind() == PersonnelCommandKind.UNPAID_LEAVE_START) {
                commands.lockSeparationDecisions();
            }
            if (merge == null) {
                holding = mergeProgress.lockHolding(employee);
            }
            if (!commands.lockEmployee(employee)) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "the employee does not exist");
            }
        }
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(ids.next()), employee, request, by, now, today, commands.eventSequence(employee));
        if (merge != null) {
            command.generatedBy(merge);
        }
        requireTargetsExist(command);
        if (command.targetOrgUnitId() != null && commands.hasOpenMergeFor(command.targetOrgUnitId(), command.effectiveDate())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN, ErrorKind.CONFLICT,
                    "the department has a merge in progress, or one dated on or before " + command.effectiveDate() + "; nobody can be placed there from then on");
        }
        PersonnelHistory all = commands.load(employee);
        PersonnelHistory history = bindRelationship(command, request, all, employee, now);
        command.actOnPost(history, () -> commands.primaryRoot(employee, now));
        PersonnelCommandRules.check(command, history);
        @Nullable ActingDuty actingTarget = validateActing(command, all);
        PersonnelCommandRules.requireOneCommandPerDay(command, all);
        PersonnelCommandRules.requirePrimaryNotOnEndingRelationship(command, all);
        PersonnelCommandRules.requireNoAwaitingOnOrBefore(command, all);
        freeze.requireNotFrozen(command);
        bases.of(command, all).ifPresent(basis -> {
            if (merge != null) {
                // The merge's transfer is exempt when the person's entity change is the one the merge's heads approved (ADR-0073 guard 1); any other change waits for its own
                // countersign. The recorder is the system actor, whose account names no employee, so the own-entry check has nobody to test.
                if (mergeProgress.approved(merge, employee, basis)) {
                    command.changeApprovedByMerge(basis.previous(), basis.next());
                } else {
                    command.requireCountersign(basis.previous(), basis.next());
                }
                return;
            }
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
        if (command.kind() == PersonnelCommandKind.START_CONCURRENT) {
            commands.insertConcurrentRoot(employee, startedPost(plans.get(VersionTable.ASSIGNMENT)), now);
        }
        for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
            commands.insertVersions(employee, plan.getKey(), plan.getValue(), command.id(), now);
        }
        if (!held && !awaiting) {
            mergeProgress.recount(holding);
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
            if (actingTarget != null) {
                effects.changeActingDuty(command, actingTarget);
            } else {
                effects.publish(command, all, today);
            }
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
        List<OrgStructureCommand> openMerges = mergeProgress.lockOpen();
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
            PersonnelCommandRules.requireNoSeparationAtOrAfter(command, history);
            Map<VersionTable, VersionPlan> plans = timelines.forWithdrawal(command, history);
            PersonnelCommandRules.requirePositionOnCommandRows(plans.get(VersionTable.ASSIGNMENT));
            Set<OrgStructureCommandId> placedBefore = mergeProgress.placedBy(openMerges, command.employeeId());
            for (Map.Entry<VersionTable, VersionPlan> plan : plans.entrySet()) {
                commands.insertVersions(command.employeeId(), plan.getKey(), plan.getValue(), command.id(), now);
            }
            // The withdrawal names no unit but can put the person back into one (one-design ruling on #1098, S1a): a placement like any other.
            if (!placedBefore.containsAll(mergeProgress.placedBy(openMerges, command.employeeId()))) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN, ErrorKind.CONFLICT,
                        "withdrawing command " + command.id() + " would put employee " + command.employeeId() + " back into a department whose merge is in progress, or joins it only after the merge day;"
                                + " withdraw it after the merge has finished");
            }
        }
        commands.update(command, now, loadedVersion);
        commands.insertTransition(COMMAND, command.id().value(), loadedVersion + 1, before.name(), command.status().name(), ActorType.USER, by.value(), today, now, command.id(), correlation());
        // A released hold stays released when its still pending command is withdrawn: the release was decided.
        commands.holdOf(command.id()).filter(hold -> hold.status() == HoldStatus.HELD).ifPresent(hold -> withdrawHold(hold, command, by, today, now));
        audit.record(new AuditEntry("organization.workforce.personnel-command.withdrawn", by, null, AuditSubject.of(AuditSubjectKind.EMPLOYEE, command.employeeId().value()),
                "command " + command.id() + " " + command.kind() + "; reason: " + command.withdrawalReason().replaceAll("\\p{Cntrl}", " ")));
    }

    /** The post a start made: the one root its first row carries. */
    private static AssignmentId startedPost(VersionPlan assignments) {
        return new AssignmentId(Objects.requireNonNull(assignments.inserts().getFirst().root(), "root"));
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

    /**
     * Binds the command to its employment relationship (ADR-0086, design section 5) and returns the history as that relationship sees it. A hire makes the relationship
     * here (the employer is read from the request once and lives on the relationship); every other kind but a change of legal entity names one of the employee's,
     * or, when the employee has exactly one, means it. A merge's transfer follows the relationship of the primary post it moves.
     */
    private PersonnelHistory bindRelationship(PersonnelCommand command, EnterPersonnelCommand request, PersonnelHistory all, EmployeeId employee, Instant now) {
        if (command.kind() == PersonnelCommandKind.CHANGE_LEGAL_ENTITY) {
            return all;
        }
        if (command.kind() == PersonnelCommandKind.HIRE) {
            EmploymentRelationshipId started = startRelationship(command, request, all, employee, now);
            command.bindTo(started);
            return movesPrimary(command, all, started) ? all.withPrimaryIn(started) : all.within(started);
        }
        EmploymentRelationshipId named = request.employmentRelationshipId();
        if (named == null) {
            named = defaultRelationship(command, all);
        }
        if (all.relationship(named).isEmpty()) {
            throw invalid("the employment relationship does not belong to the employee");
        }
        command.bindTo(named);
        boolean primaryTransfer = command.kind() == PersonnelCommandKind.TRANSFER && (request.targetAssignmentId() == null
                || all.primaryRoot().map(root -> root.id().equals(request.targetAssignmentId())).orElse(false));
        if (primaryTransfer && movesPrimary(command, all, named)) {
            return all.withPrimaryIn(named);
        }
        if (command.kind() == PersonnelCommandKind.REINSTATE && movesPrimary(command, all, named)) {
            return all.withPrimaryIn(named);
        }
        return all.within(named);
    }

    /** The command writes the next version of the primary post, and the version in force that day belongs to another relationship (design 6.3): the post moves. */
    private static boolean movesPrimary(PersonnelCommand command, PersonnelHistory all, EmploymentRelationshipId target) {
        if (command.kind() != PersonnelCommandKind.TRANSFER && command.targetOrgUnitId() == null) {
            return false;
        }
        VersionRow current = all.assignmentOn(command.effectiveDate());
        return current != null && current.covers(command.effectiveDate()) && current.relationshipId() != null && !current.relationshipId().equals(target);
    }

    private EmploymentRelationshipId defaultRelationship(PersonnelCommand command, PersonnelHistory all) {
        if (command.orgStructureCommandId() != null) {
            VersionRow primary = all.assignmentOn(command.effectiveDate());
            if (primary != null && primary.relationshipId() != null) {
                return primary.relationshipId();
            }
        }
        if (all.relationships().size() == 1) {
            return all.relationships().getFirst().id();
        }
        throw invalid("a " + command.kind() + " names the employment relationship it is about");
    }

    private EmploymentRelationshipId startRelationship(PersonnelCommand command, EnterPersonnelCommand request, PersonnelHistory all, EmployeeId employee, Instant now) {
        LegalEntityId employer = request.targetEmployerLegalEntityId();
        if (employer == null) {
            throw invalid("a hire names the employer");
        }
        if (!commands.legalEntityIsActive(employer)) {
            throw invalid("the employer does not exist or is not active");
        }
        EmploymentRelationshipId predecessor = request.predecessorRelationshipId();
        RelationshipRow earlier = all.relationships().stream().filter(r -> r.employerLegalEntityId().equals(employer)).findFirst().orElse(null);
        if (earlier != null && !all.currentEmployeeRows(earlier.id()).isEmpty()) {
            throw invalid("the employee already has a relationship with this employer; a return is a REINSTATE on it");
        }
        if (earlier != null && !Objects.equals(earlier.predecessorId(), predecessor)) {
            throw invalid("the employee's earlier hire by this employer was withdrawn with another predecessor (relationship " + earlier.id() + "); a re-entry names the same one");
        }
        LocalDate day = command.effectiveDate();
        if (predecessor != null) {
            if (all.relationship(predecessor).isEmpty()) {
                throw invalid("the predecessor relationship does not belong to the employee");
            }
            if (all.commands().stream().noneMatch(o -> o.kind() == PersonnelCommandKind.SEPARATE && o.status() != CommandStatus.WITHDRAWN
                    && o.employmentRelationshipId() != null && o.employmentRelationshipId().equals(predecessor) && o.effectiveDate().equals(day))) {
                throw invalid("the predecessor relationship is not separated on " + day + "; enter that separation first");
            }
        }
        boolean primaryInService = all.assignmentOn(day) != null && all.assignmentOn(day).covers(day);
        if (!all.relationships().isEmpty()) {
            if (command.targetOrgUnitId() == null && !primaryInService) {
                throw invalid("a hire without a post needs the employee's primary post in service on " + day);
            }
        }
        if (earlier != null) {
            return earlier.id();  // design 4.2-9: a relationship left without a live version by a withdrawn hire is re-used, never deleted
        }
        EmploymentRelationshipId id = new EmploymentRelationshipId(ids.next());
        commands.insertRelationship(id, employee, employer, predecessor, now);
        return id;
    }

    private static DomainException invalid(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, message);
    }

    /** Every kind but the hire of a new person is about a named employee and carries no profile (the profile of an existing employee is changed by its own use case). */
    private static EmployeeId existingEmployee(EnterPersonnelCommand request) {
        if (request.employeeId() == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "the employee is required");
        }
        if (request.displayName() != null || request.gender() != null || request.email() != null || request.wecomUserId() != null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE, "only a hire carries a profile");
        }
        return request.employeeId();
    }

    private @Nullable ActingDuty validateActing(PersonnelCommand command, PersonnelHistory all) {
        if (command.kind() == PersonnelCommandKind.ACTING_START) {
            // Separation closes the person's duties across relationships, so a start also checks the person's whole history.
            PersonnelCommandRules.check(command, all);
            ActingDutyRules.requireAvailable(command, all, duties.findOverlap(command.employeeId(), Objects.requireNonNull(command.actingCoveredOrgUnitId()),
                    command.actingCoveredPositionId(), command.effectiveDate(), Objects.requireNonNull(command.actingEndsBeforeDate())));
        } else if (command.kind() == PersonnelCommandKind.ACTING_END || command.kind() == PersonnelCommandKind.ACTING_EXTEND) {
            ActingDuty target = ActingDutyRules.requireTarget(command, duties.find(Objects.requireNonNull(command.targetActingDutyId())).orElse(null));
            if (command.kind() == PersonnelCommandKind.ACTING_END) {
                ActingDutyRules.requireEnd(command, target, all.commands());
            } else {
                ActingDutyRules.requireExtend(command, target, all, duties.findOverlap(command.employeeId(), target.coveredOrgUnitId(),
                        target.coveredPositionId(), target.startsOn(), Objects.requireNonNull(command.actingEndsBeforeDate())));
            }
            return target;
        }
        return null;
    }

    private void requireTargetsExist(PersonnelCommand command) {
        if (command.targetOrgUnitId() != null && !commands.orgUnitExists(command.targetOrgUnitId())
                || command.targetPositionId() != null && !commands.positionExists(command.targetPositionId())
                || command.targetReportsToEmployeeId() != null && !commands.employeeExists(command.targetReportsToEmployeeId())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID, ErrorKind.UNPROCESSABLE,
                    "a department, position or manager the command names does not exist");
        }
        if (command.actingCoveredOrgUnitId() != null && !commands.orgUnitExists(command.actingCoveredOrgUnitId())
                || command.actingCoveredPositionId() != null && !commands.positionExists(command.actingCoveredPositionId())
                || command.actingCoveredEmployeeId() != null && !commands.employeeExists(command.actingCoveredEmployeeId())) {
            throw invalid("a covered department, position or employee does not exist");
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
