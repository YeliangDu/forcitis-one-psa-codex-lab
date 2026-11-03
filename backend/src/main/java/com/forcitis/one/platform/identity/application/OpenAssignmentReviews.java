package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The review items a changed assignment raises. A sensitive-role review per changed field if the account holds a sensitive role when the event is
 * handled (ADR-0032 decision 5); one scope review per department change if it holds a role scoped to custom departments, addressed to the head of
 * the new department (ADR-0032 decision 4; ADR-0032 ruling on #730). The account row is locked first, so two events of one command cannot both pass the existence check; the key is
 * (kind, person, command, field), so a repeated delivery adds nothing. Runs inside the listener's transaction.
 */
@Component
class OpenAssignmentReviews {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAssignmentReviews.class);
    private static final int ESCALATION_WORKDAYS = 5;

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PermissionAdjustmentRepository adjustments;
    private final WorkdayDeadlines deadlines;
    private final DomainEvents events;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;
    private final OrgDirectory directory;
    private final WecomGateway wecom;

    OpenAssignmentReviews(AccountRepository accounts, RoleRepository roles, PermissionAdjustmentRepository adjustments, WorkdayDeadlines deadlines,
            DomainEvents events, IdGenerator ids, Clock clock, BusinessZone zone, OrgDirectory directory, WecomGateway wecom) {
        this.accounts = accounts;
        this.roles = roles;
        this.adjustments = adjustments;
        this.deadlines = deadlines;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
        this.directory = directory;
        this.wecom = wecom;
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
            Instant dueAt = escalationDueAt(trigger, now);
            PermissionAdjustment item = PermissionAdjustment.assignmentChangeReview(new PermissionAdjustmentId(ids.next()), user, command, field,
                    beforeOrgUnit, afterOrgUnit, beforePosition, afterPosition, dueAt, now);
            adjustments.insert(item);
            events.publish(item);
        });
    }

    void openScopeReviewFor(UserId user, EventEnvelope trigger, PersonnelCommandId command, OrgUnitId beforeOrgUnit, OrgUnitId afterOrgUnit,
            @Nullable PositionId beforePosition, @Nullable PositionId afterPosition) {
        accounts.findForUpdate(user).ifPresent(account -> {
            boolean holdsScopedRole = accounts.openGrants(user).stream()
                    .anyMatch(grant -> roles.findById(grant.roleId()).map(role -> role.accessScope() == AccessScopeKind.CUSTOM_ORG_UNITS).orElse(false));
            if (!holdsScopedRole || adjustments.existsForTrigger(PermissionAdjustmentKind.SCOPE_REVIEW, user, command, TriggerFieldKind.ORG_UNIT)) {
                return;
            }
            Instant now = clock.instant();
            EmployeeId head = directory.findOrgUnitHead(afterOrgUnit).orElse(null);
            PermissionAdjustment item = PermissionAdjustment.departmentChangeScopeReview(new PermissionAdjustmentId(ids.next()), user, command,
                    beforeOrgUnit, afterOrgUnit, beforePosition, afterPosition, head, escalationDueAt(trigger, now), now);
            adjustments.insert(item);
            events.publish(item);
            assign(item, head, trigger);
        });
    }

    /** One card to the new head, queued in this transaction; none when the department has no head or the head has no WeCom user id. */
    private void assign(PermissionAdjustment item, @Nullable EmployeeId head, EventEnvelope trigger) {
        if (head == null) {
            LOG.error("ALERT P3 platform.identity event {} has no head of the new department: the scope review item {} has no recipient and no card",
                    trigger.eventId(), item.id().value());
            return;
        }
        directory.findEmployee(head).ifPresentOrElse(
                profile -> wecom.send(new WecomCard(List.of(profile.wecomUserId()), "待调整权限：范围复核", "有员工调入你负责的部门，请复核其数据范围授权。",
                        "https://one.forcitis.com/", DeliverySubject.of(DeliveryPurpose.PERMISSION_ADJUSTMENT_ASSIGNED, item.id().value()))),
                () -> LOG.error("ALERT P3 platform.identity event {} the head of the new department has no profile: scope review item {} has no card",
                        trigger.eventId(), item.id().value()));
    }

    private Instant escalationDueAt(EventEnvelope trigger, Instant now) {
        LegalEntityId legalEntity = trigger.legalEntityId();
        if (legalEntity == null) {
            LOG.error("ALERT P3 platform.identity event {} has no legal entity: the escalation deadline follows the default work calendar",
                    trigger.eventId());
        }
        return deadlines.endOfWorkdayAfter(zone.dateOf(clock.instant()), ESCALATION_WORKDAYS, legalEntity);
    }
}
