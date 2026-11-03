package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per item for the two daily jobs, so one failure never stops the others. Every step re-reads the item and does nothing when it is no
 * longer in the state the job found it in (a rerun or a second node changes nothing); a lost race on the version fails the step and the job's next run
 * finds the item again. Nothing here grants or takes back an authorization.
 */
@Component
class PermissionAdjustmentTransitions {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionAdjustmentTransitions.class);
    private static final int ESCALATION_WORKDAYS = 5;

    private final PermissionAdjustmentRepository adjustments;
    private final AccountEmployees accountEmployees;
    private final OrgDirectory directory;
    private final LegalEntityOnDay legalEntities;
    private final HoldsSensitiveRole sensitiveRoles;
    private final WorkdayDeadlines deadlines;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PermissionAdjustmentTransitions(PermissionAdjustmentRepository adjustments, AccountEmployees accountEmployees, OrgDirectory directory,
            LegalEntityOnDay legalEntities, HoldsSensitiveRole sensitiveRoles, WorkdayDeadlines deadlines, DomainEvents events, IdGenerator ids,
            Clock clock, BusinessZone zone) {
        this.adjustments = adjustments;
        this.accountEmployees = accountEmployees;
        this.directory = directory;
        this.legalEntities = legalEntities;
        this.sensitiveRoles = sensitiveRoles;
        this.deadlines = deadlines;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    /** An upheld item at its expiry turns EXPIRED; a person not held that day is decided about at once, a held one waits for {@link #decide}. */
    @Transactional
    void expire(PermissionAdjustmentId id) {
        Instant now = clock.instant();
        PermissionAdjustment item = adjustments.findById(id).orElse(null);
        if (item == null || item.status() != PermissionAdjustmentStatus.UPHELD || item.upholdExpiresAt() == null || now.isBefore(item.upholdExpiresAt())) {
            return;
        }
        EmployeeId person = personOf(item);
        boolean held = person != null && directory.availabilityOn(person, today(now)).separationHeld();
        item.expire(now, held);
        adjustments.update(item);
        events.publish(item);
        if (!held) {
            regenerateIfStillNeeded(item, person, now);
        }
    }

    /** An item that expired while its person was held: decided about once the separation is no longer held (invariant 26). */
    @Transactional
    void decide(PermissionAdjustmentId id) {
        Instant now = clock.instant();
        PermissionAdjustment item = adjustments.findById(id).orElse(null);
        if (item == null || item.status() != PermissionAdjustmentStatus.EXPIRED || item.regenerationDecidedAt() != null) {
            return;
        }
        EmployeeId person = personOf(item);
        if (person != null && directory.availabilityOn(person, today(now)).separationHeld()) {
            return;
        }
        item.decideRegeneration(now);
        adjustments.update(item);
        regenerateIfStillNeeded(item, person, now);
    }

    /** An open item past its due time is marked escalated, once; it stays open. */
    @Transactional
    void escalate(PermissionAdjustmentId id) {
        Instant now = clock.instant();
        PermissionAdjustment item = adjustments.findById(id).orElse(null);
        if (item == null || item.status() != PermissionAdjustmentStatus.OPEN || item.escalatedAt() != null || now.isBefore(item.escalationDueAt())) {
            return;
        }
        item.escalate(now);
        adjustments.update(item);
        events.publish(item);
    }

    private @Nullable EmployeeId personOf(PermissionAdjustment item) {
        return item.user() == null ? null : accountEmployees.employeeOf(item.user()).orElse(null);
    }

    private LocalDate today(Instant now) {
        return zone.dateOf(now);
    }

    /** A successor follows only for a person who is still employed and still holds a sensitive role (a person who left or lost the role needs no review). */
    private void regenerateIfStillNeeded(PermissionAdjustment expired, @Nullable EmployeeId person, Instant now) {
        boolean employed = person != null && directory.employmentOn(person, today(now)).map(status -> status == EmploymentStatus.EMPLOYED).orElse(false);
        if (!employed || !sensitiveRoles.test(expired.user())) {
            return;
        }
        PermissionAdjustmentId id = new PermissionAdjustmentId(ids.next());
        PermissionAdjustment next = PermissionAdjustment.expiryReview(id, expired, escalationDueAt(id, person, now), now);
        adjustments.insert(next);
        events.publish(next);
    }

    /** The fifth workday after the handling day in the calendar of the person's legal entity that day; the default calendar, with an alert, when none can be named. */
    private Instant escalationDueAt(PermissionAdjustmentId item, EmployeeId person, Instant now) {
        LocalDate day = today(now);
        LegalEntityId legalEntity = null;
        try {
            legalEntity = legalEntities.of(person, day);
        } catch (DomainException unresolved) {
            if (unresolved.errorCode() != WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED) {
                throw unresolved;
            }
            LOG.error("ALERT P3 platform.identity expiry job: the person of the regenerated item {} has no resolvable legal entity: its escalation deadline follows the default work calendar",
                    item.value());
        }
        return deadlines.endOfWorkdayAfter(day, ESCALATION_WORKDAYS, legalEntity);
    }
}
