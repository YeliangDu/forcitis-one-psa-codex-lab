package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.events.RoleRevised;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A role was revised (RULE-387): each OPEN item that asks to review a dissolved or merged-away org unit of this role turns ADJUSTED, with the audit entry of
 * the named role version, when that version no longer lists the unit. The event also names the kept version when nothing changed, so a version that still
 * lists the unit leaves the item as it is. An UPHELD item is not touched. Nobody is named as the handler.
 */
@SystemActorName("platform.identity.permission-adjustment")
@Component
class AdjustInactiveOrgUnitReviewsOnRoleRevised {

    private static final String ACTOR = AdjustInactiveOrgUnitReviewsOnRoleRevised.class.getAnnotation(SystemActorName.class).value();

    private final PermissionAdjustmentRepository adjustments;
    private final RoleRepository roles;
    private final DomainEvents events;

    AdjustInactiveOrgUnitReviewsOnRoleRevised(PermissionAdjustmentRepository adjustments, RoleRepository roles, DomainEvents events) {
        this.adjustments = adjustments;
        this.roles = roles;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(RoleRevised event) {
        try (WorkContext.Scope scope = WorkContext.enter(WorkContext.correlationId().orElseThrow(), WorkContext.causationId().orElse(null), ACTOR)) {
            for (PermissionAdjustmentId id : adjustments.openForInactiveUnitsOf(event.roleId())) {
                PermissionAdjustment item = adjustments.findById(id).orElseThrow();
                if (roles.versionLists(event.roleVersionId(), item.inactiveOrgUnit())) {
                    continue;
                }
                item.markAdjustedBySystem(roles.versionAuditEntry(event.roleVersionId()));
                adjustments.update(item);
                events.publish(item);
            }
        }
    }
}
