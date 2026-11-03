package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.time.Clock;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A correction that undoes a wrong separation (the person had no department before, has one today; ADR-0082 decision 2) raises one
 * sensitive-role review for the person if the account holds a sensitive role when the event is handled; never a scope review, never one per
 * role. A correction after which the person is not employed raises nothing. A repeated delivery, or another event of the same command,
 * adds nothing. Corrections that move an employed person between departments are not handled here.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class HandleEmployeeCorrected {

    private static final Logger LOG = LoggerFactory.getLogger(HandleEmployeeCorrected.class);
    private static final String ACTOR = HandleEmployeeCorrected.class.getAnnotation(SystemActorName.class).value();
    private static final int ESCALATION_WORKDAYS = 5;

    private final AccountEmployees accountEmployees;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PermissionAdjustmentRepository adjustments;
    private final WorkdayDeadlines deadlines;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    HandleEmployeeCorrected(AccountEmployees accountEmployees, AccountRepository accounts, RoleRepository roles,
            PermissionAdjustmentRepository adjustments, WorkdayDeadlines deadlines, DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.accountEmployees = accountEmployees;
        this.accounts = accounts;
        this.roles = roles;
        this.adjustments = adjustments;
        this.deadlines = deadlines;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @ApplicationModuleListener
    void on(EmployeeCorrected event) {
        boolean undoesSeparation = event.triggerFieldKinds().contains(TriggerFieldKind.ORG_UNIT) && event.previousOrgUnitId() == null
                && event.newOrgUnitId() != null;
        if (!undoesSeparation) {
            return;
        }
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            accountEmployees.accountOf(event.employeeId()).ifPresent(user -> review(user, event));
        }
    }

    /** The account row is locked first, so two events of one command cannot both pass the existence check. */
    private void review(UserId user, EmployeeCorrected event) {
        accounts.findForUpdate(user).ifPresent(account -> {
            boolean holdsSensitiveRole = accounts.openGrants(user).stream()
                    .anyMatch(grant -> roles.findById(grant.roleId()).map(SensitivePermissions::roleIsSensitive).orElse(false));
            if (!holdsSensitiveRole || adjustments.existsForTrigger(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW, user, event.personnelCommandId(),
                    TriggerFieldKind.ORG_UNIT)) {
                return;
            }
            Instant now = clock.instant();
            LegalEntityId legalEntity = event.envelope().legalEntityId();
            if (legalEntity == null) {
                LOG.error("ALERT P3 platform.identity event {} has no legal entity: the escalation deadline follows the default work calendar",
                        event.envelope().eventId());
            }
            Instant dueAt = deadlines.endOfWorkdayAfter(zone.dateOf(now), ESCALATION_WORKDAYS, legalEntity);
            PermissionAdjustment item = PermissionAdjustment.separationCorrectionReview(new PermissionAdjustmentId(ids.next()), user,
                    event.personnelCommandId(), event.newOrgUnitId(), event.newPositionId(), dueAt, now);
            adjustments.insert(item);
            events.publish(item);
        });
    }
}
