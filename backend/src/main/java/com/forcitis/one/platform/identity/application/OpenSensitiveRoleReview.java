package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One sensitive-role review for a person whose assignment changed, if the account holds a sensitive role when the event is handled (ADR-0032
 * decision 5). The account row is locked first, so two events of one command cannot both pass the existence check; the key is
 * (kind, person, command, field), so a repeated delivery adds nothing. Runs inside the listener's transaction.
 */
@Component
class OpenSensitiveRoleReview {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSensitiveRoleReview.class);
    private static final int ESCALATION_WORKDAYS = 5;

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PermissionAdjustmentRepository adjustments;
    private final WorkdayDeadlines deadlines;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    OpenSensitiveRoleReview(AccountRepository accounts, RoleRepository roles, PermissionAdjustmentRepository adjustments, WorkdayDeadlines deadlines,
            DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.accounts = accounts;
        this.roles = roles;
        this.adjustments = adjustments;
        this.deadlines = deadlines;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    void openFor(UserId user, EventEnvelope trigger, PersonnelCommandId command, TriggerFieldKind field, @Nullable OrgUnitId beforeOrgUnit,
            OrgUnitId afterOrgUnit, @Nullable PositionId beforePosition, @Nullable PositionId afterPosition) {
        accounts.findForUpdate(user).ifPresent(account -> {
            boolean holdsSensitiveRole = accounts.openGrants(user).stream()
                    .anyMatch(grant -> roles.findById(grant.roleId()).map(SensitivePermissions::roleIsSensitive).orElse(false));
            if (!holdsSensitiveRole || adjustments.existsForTrigger(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW, user, command, field)) {
                return;
            }
            Instant now = clock.instant();
            LegalEntityId legalEntity = trigger.legalEntityId();
            if (legalEntity == null) {
                LOG.error("ALERT P3 platform.identity event {} has no legal entity: the escalation deadline follows the default work calendar",
                        trigger.eventId());
            }
            Instant dueAt = deadlines.endOfWorkdayAfter(zone.dateOf(now), ESCALATION_WORKDAYS, legalEntity);
            PermissionAdjustment item = PermissionAdjustment.assignmentChangeReview(new PermissionAdjustmentId(ids.next()), user, command, field,
                    beforeOrgUnit, afterOrgUnit, beforePosition, afterPosition, dueAt, now);
            adjustments.insert(item);
            events.publish(item);
        });
    }
}
