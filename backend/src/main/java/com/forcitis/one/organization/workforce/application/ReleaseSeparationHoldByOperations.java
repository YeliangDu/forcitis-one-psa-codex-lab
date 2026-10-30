package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The release of a held separation by the two-person operations command (ADR-0068 item 2.4), the way out when the interface refuses because no
 * available super administrator would remain. It runs only when releasing leaves none, and only with an operator and a witness who stay: locally
 * employed, not leaving (held, or released and waiting for the date), not on unpaid leave (user ruling 2026-10-30, decision fragment 2026-10-30-131322); when one or more would remain, the
 * single release in the interface is the path. It takes the same separation lock as every other part of the decision and writes the same facts as the
 * interface release, with the system as actor and no releasing account.
 */
@Component
class ReleaseSeparationHoldByOperations {
    private static final String SYSTEM_ACTOR = "organization.workforce.operations";

    private final PersonnelCommandRepository commands;
    private final SeparationReview review;
    private final OrgDirectory directory;
    private final HeldSeparationRelease release;
    private final Clock clock;
    private final BusinessZone zone;

    ReleaseSeparationHoldByOperations(PersonnelCommandRepository commands, SeparationReview review, OrgDirectory directory, HeldSeparationRelease release,
                                      Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.review = review;
        this.directory = directory;
        this.release = release;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void execute(WorkforceOperationAttestation attestation, SeparationHoldId holdId, String reason) {
        Instant now = clock.instant();
        commands.lockSeparationDecisions();
        SeparationHold found = commands.findHold(holdId).orElseThrow(ReleaseSeparationHoldByOperations::notFound);
        PersonnelCommand command = commands.find(found.commandId()).orElseThrow(ReleaseSeparationHoldByOperations::notFound);
        EmployeeId employee = command.employeeId();
        commands.lockEmployee(employee);
        SeparationHold hold = commands.findHold(holdId).orElseThrow(ReleaseSeparationHoldByOperations::notFound);
        command = commands.find(found.commandId()).orElseThrow(ReleaseSeparationHoldByOperations::notFound);
        long holdVersion = hold.version();
        long commandVersion = command.version();
        hold.releaseByOperations(reason, now);
        Set<EmployeeId> leaving = commands.heldOrReleasedEmployees();
        int remaining = review.review(List.of(employee), leaving, null).remainingAdministrators();
        if (remaining > 0) {
            throw refused(remaining + " available super_admin would remain: release this hold with the single release in the interface, not with this command");
        }
        LocalDate today = zone.dateOf(now);
        requireRetained(attestation.operator(), "operator", leaving, today);
        requireRetained(attestation.witness(), "witness", leaving, today);
        release.execute(command, commandVersion, hold, holdVersion, HeldSeparationRelease.Releaser.operations(SystemActors.idOf(SYSTEM_ACTOR)), now,
                "reason: " + hold.releaseReason().replaceAll("\\p{Cntrl}", " ") + "; route: operations command; operatorId=" + attestation.operator()
                        + " witnessId=" + attestation.witness() + "; command: " + command.id() + "; hold: " + hold.id() + "; remaining super_admin: " + remaining);
    }

    /** Retained: locally employed, not leaving and not on unpaid leave (ADR-0068 section 3; a released separation waiting for its date is leaving). */
    private void requireRetained(EmployeeId person, String role, Set<EmployeeId> leaving, LocalDate today) {
        boolean employed = directory.findEmployee(person).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
        if (leaving.contains(person) || !employed || directory.isOnUnpaidLeave(person, today)) {
            throw refused("the " + role + " does not stay: they must be locally employed, not leaving and not on unpaid leave");
        }
    }

    private static DomainException refused(String message) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED, ErrorKind.UNPROCESSABLE, message);
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND, ErrorKind.NOT_FOUND, "no such separation hold");
    }
}
