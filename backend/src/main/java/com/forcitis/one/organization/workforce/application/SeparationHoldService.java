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
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHolds;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
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
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single release of a held separation (ADR-0068 item 2.3). It takes the separation lock first, then asks the review with the held set: a releaser
 * who is not retained (not an available administrator, held themself, or on leave) and a release that would leave no available super administrator are
 * refused; one left is allowed with an alert. The command then takes effect at its original date through the version mechanism, and
 * the hold, the transitions, the audit entry and the two events are written in the same transaction. It is never held again.
 */
@Component
class SeparationHoldService implements SeparationHolds {
    static final String PERMISSION = "organization.workforce:separation-hold:approve";
    private static final Logger LOG = LoggerFactory.getLogger(SeparationHoldService.class);
    private static final String COMMAND = "PersonnelCommand";
    private static final String HOLD = "SeparationHold";

    private final PersonnelCommandRepository commands;
    private final PersonnelPlans timelines;
    private final SeparationReview review;
    private final LegalEntityResolutionSources legalEntities;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    SeparationHoldService(PersonnelCommandRepository commands, PersonnelPlans timelines, SeparationReview review, LegalEntityResolutionSources legalEntities,
                          AuditLog audit, DomainEvents events, CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.timelines = timelines;
        this.review = review;
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
    public void release(ReleaseSeparationHold request) {
        Instant now = clock.instant();
        LocalDate today = zone.dateOf(now);
        UserId by = currentUser.userId();
        commands.lockSeparationDecisions();
        SeparationHold found = commands.findHold(request.separationHoldId()).orElseThrow(SeparationHoldService::notFound);
        PersonnelCommand command = commands.find(found.commandId()).orElseThrow(SeparationHoldService::notFound);
        EmployeeId employee = command.employeeId();
        commands.lockEmployee(employee);
        SeparationHold hold = commands.findHold(request.separationHoldId()).orElseThrow(SeparationHoldService::notFound);
        command = commands.find(found.commandId()).orElseThrow(SeparationHoldService::notFound);
        long holdVersion = hold.version();
        long commandVersion = command.version();
        hold.release(by, request.reason(), now);
        SeparationReview.Verdict verdict = review.review(List.of(employee), commands.heldEmployees(), by);
        if (!verdict.actorRetained()) {
            throw refused("the releaser is not a retained administrator: not an available holder, held, or on unpaid leave");
        }
        if (verdict.remainingAdministrators() == 0) {
            throw refused("no available super_admin would remain");
        }
        String reason = hold.releaseReason().replaceAll("\\p{Cntrl}", " ");
        String commandBefore = command.status().name();
        command.releaseHeld(now);
        PersonnelHistory history = commands.load(employee);
        AuditEntryId auditEntry = audit.record(new AuditEntry("organization.workforce.separation-hold.released", by, null,
                AuditSubject.of(AuditSubjectKind.EMPLOYEE, employee.value()), "reason: " + reason + "; session mode: "
                + (verdict.sessionMode().isEmpty() ? "none" : verdict.sessionMode()) + "; command: " + command.id() + "; hold: " + hold.id()
                + "; remaining super_admin: " + verdict.remainingAdministrators()));
        for (Map.Entry<VersionTable, VersionPlan> plan : timelines.forEntry(command, history, auditEntry).entrySet()) {
            commands.insertVersions(employee, plan.getKey(), plan.getValue(), command.id(), now);
        }
        commands.update(command, commandVersion);
        commands.updateHold(hold, now, holdVersion);
        commands.insertTransition(COMMAND, command.id().value(), commandVersion + 1, commandBefore, command.status().name(), by, today, now, command.id(), correlation());
        commands.insertTransition(HOLD, hold.id().value(), hold.version(), "HELD", hold.status().name(), by, today, now, command.id(), correlation());
        LegalEntityId legalEntity = LegalEntityResolution.resolve(legalEntities, employee, command.effectiveDate().minusDays(1)).orElse(null);
        command.recordEffect(null, legalEntity);
        command.recordReleased(hold.id(), by, legalEntity);
        events.publish(command);
        commands.saveEventSequence(employee, command.eventSequenceAfterSave());
        if (verdict.remainingAdministrators() == 1) {
            LOG.error("ALERT organization.workforce separation hold {} released by user {}: only 1 available super_admin remains", hold.id(), by.value());
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED, ErrorKind.UNPROCESSABLE, message);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such separation hold");
    }

    private UUID correlation() {
        return WorkContext.correlationId().orElseGet(ids::next);
    }
}
