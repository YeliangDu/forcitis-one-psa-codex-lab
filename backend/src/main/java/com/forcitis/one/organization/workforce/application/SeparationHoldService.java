package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHolds;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single release of a held separation (ADR-0068 item 2.3). It takes the separation lock first, then asks the review with the held set: a releaser
 * who is not retained (not an available administrator, leaving themself, or on leave) and a release that would leave no available super administrator are
 * refused; one left is allowed with an alert. The held set counts, besides the held, every administrator whose separation was released and still waits
 * for its date and every one with an entered unpaid-leave start that has not started (the one count of PersonnelCommandRepository#excludedEmployees). The versions are written at the original date and the command takes effect now or, when that date is later, waits for the due-date job;
 * the hold, the transitions, the audit entry and the events are written in the same transaction. It is never held again.
 */
@Component
class SeparationHoldService implements SeparationHolds {
    static final String PERMISSION = WorkforcePermissions.SEPARATION_HOLD_APPROVE;

    private final PersonnelCommandRepository commands;
    private final SeparationHoldReview review;
    private final HeldSeparationRelease release;
    private final OrgMergeProgress mergeProgress;
    private final SeparationHoldNotice notice;
    private final CurrentUser currentUser;
    private final Clock clock;

    SeparationHoldService(PersonnelCommandRepository commands, SeparationHoldReview review, HeldSeparationRelease release, OrgMergeProgress mergeProgress, SeparationHoldNotice notice,
                          CurrentUser currentUser, Clock clock) {
        this.commands = commands;
        this.review = review;
        this.release = release;
        this.mergeProgress = mergeProgress;
        this.notice = notice;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    public void release(ReleaseSeparationHold request) {
        Instant now = clock.instant();
        UserId by = currentUser.userId();
        commands.lockSeparationDecisions();
        SeparationHold found = commands.findHold(request.separationHoldId()).orElseThrow(SeparationHoldService::notFound);
        PersonnelCommand command = commands.find(found.commandId()).orElseThrow(SeparationHoldService::notFound);
        EmployeeId employee = command.employeeId();
        List<OrgStructureCommandId> holding = mergeProgress.lockHolding(employee);
        commands.lockEmployee(employee);
        SeparationHold hold = commands.findHold(request.separationHoldId()).orElseThrow(SeparationHoldService::notFound);
        command = commands.find(found.commandId()).orElseThrow(SeparationHoldService::notFound);
        long holdVersion = hold.version();
        long commandVersion = command.version();
        hold.release(by, request.reason(), now);
        SeparationReview.Verdict verdict = review.review(employee, by);
        if (!verdict.actorRetained()) {
            throw refused("the releaser is not a retained administrator: not an available holder, held, or on unpaid leave");
        }
        if (verdict.remainingAdministrators() == 0) {
            throw refused("no available super_admin would remain");
        }
        String reason = hold.releaseReason().replaceAll("\\p{Cntrl}", " ");
        release.execute(command, commandVersion, hold, holdVersion, HeldSeparationRelease.Releaser.user(by), now, "reason: " + reason + "; session mode: "
                + (verdict.sessionMode().isEmpty() ? "none" : verdict.sessionMode()) + "; command: " + command.id() + "; hold: " + hold.id()
                + "; remaining super_admin: " + verdict.remainingAdministrators(), holding);
        if (verdict.remainingAdministrators() == 1) {
            notice.remainingOne(hold, verdict.noticeRecipients(), commands.heldEmployees(), by);
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED, ErrorKind.UNPROCESSABLE, message);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such separation hold");
    }
}
