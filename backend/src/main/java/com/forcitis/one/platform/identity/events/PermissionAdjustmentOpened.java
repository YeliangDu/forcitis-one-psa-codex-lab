package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.RoleId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A review item was raised: for the authorizations of one person (userId), by a changed assignment or as the successor of an expired item, or for one role
 * (roleId, orgUnitId) whose custom org unit was dissolved or merged away (originKind ORG_UNIT_INACTIVE, triggerOrgStructureCommandId); it only reminds,
 * nothing is granted or taken back. The subject is exactly one of the two, as the table's ck_permission_adjustments_subject requires.
 */
public record PermissionAdjustmentOpened(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, @Nullable UserId userId,
                                         @Nullable RoleId roleId, @Nullable OrgUnitId orgUnitId, PermissionAdjustmentKind kind,
                                         PermissionAdjustmentOrigin originKind, @Nullable PersonnelCommandId triggerPersonnelCommandId,
                                         @Nullable OrgStructureCommandId triggerOrgStructureCommandId,
                                         @Nullable TriggerFieldKind triggerFieldKind, @Nullable OrgUnitId beforeOrgUnitId, @Nullable OrgUnitId afterOrgUnitId,
                                         @Nullable PositionId beforePositionId, @Nullable PositionId afterPositionId,
                                         @Nullable PermissionAdjustmentId originalPermissionAdjustmentId, Instant escalationDueAt) implements DomainEvent, AuditDetail {

    public PermissionAdjustmentOpened {
        boolean aboutRole = originKind == PermissionAdjustmentOrigin.ORG_UNIT_INACTIVE;
        if (aboutRole ? userId != null || roleId == null || orgUnitId == null || triggerOrgStructureCommandId == null || triggerPersonnelCommandId != null
                : userId == null || roleId != null || orgUnitId != null || triggerOrgStructureCommandId != null) {
            throw new IllegalArgumentException("the subject and the trigger of an item do not fit its origin " + originKind);
        }
    }

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " origin " + originKind
                + (userId == null ? "; role " + roleId.value() + " org unit " + orgUnitId.value() : "; user " + userId.value())
                + (triggerPersonnelCommandId == null ? "" : "; trigger command " + triggerPersonnelCommandId.value() + (triggerFieldKind == null ? "" : " field " + triggerFieldKind))
                + (triggerOrgStructureCommandId == null ? "" : "; trigger org structure command " + triggerOrgStructureCommandId.value())
                + (beforeOrgUnitId == null ? "" : "; before org " + beforeOrgUnitId.value()
                        + (beforePositionId == null ? "" : " position " + beforePositionId.value()))
                + (afterOrgUnitId == null ? "" : "; after org " + afterOrgUnitId.value() + (afterPositionId == null ? "" : " position " + afterPositionId.value()))
                + (originalPermissionAdjustmentId == null ? "" : "; regenerates " + originalPermissionAdjustmentId.value())
                + "; escalation due " + escalationDueAt;
    }
}
