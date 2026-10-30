package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHolds;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    private final PersonnelCommandRepository commands;
    private final SeparationReview review;
    private final HeldSeparationRelease release;
    private final CurrentUser currentUser;
    private final Clock clock;

    SeparationHoldService(PersonnelCommandRepository commands, SeparationReview review, HeldSeparationRelease release, CurrentUser currentUser, Clock clock) {
        this.commands = commands;
        this.review = review;
        this.release = release;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public void release(ReleaseSeparationHold request) {
        Instant now = clock.instant();
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
        release.execute(command, commandVersion, hold, holdVersion, HeldSeparationRelease.Releaser.user(by), now, "reason: " + reason + "; session mode: "
                + (verdict.sessionMode().isEmpty() ? "none" : verdict.sessionMode()) + "; command: " + command.id() + "; hold: " + hold.id()
                + "; remaining super_admin: " + verdict.remainingAdministrators());
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
}
