package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A review item for the authorizations of one person was raised, by a changed assignment or as the successor of an expired item; it only reminds, nothing is granted or taken back. */
public record PermissionAdjustmentOpened(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, UserId userId, PermissionAdjustmentKind kind,
                                         PermissionAdjustmentOrigin originKind, @Nullable PersonnelCommandId triggerPersonnelCommandId,
                                         @Nullable TriggerFieldKind triggerFieldKind, @Nullable OrgUnitId beforeOrgUnitId, @Nullable OrgUnitId afterOrgUnitId,
                                         @Nullable PositionId beforePositionId, @Nullable PositionId afterPositionId,
                                         @Nullable PermissionAdjustmentId originalPermissionAdjustmentId, Instant escalationDueAt) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " origin " + originKind + "; user " + userId.value()
                + (triggerPersonnelCommandId == null ? "" : "; trigger command " + triggerPersonnelCommandId.value() + (triggerFieldKind == null ? "" : " field " + triggerFieldKind))
                + (beforeOrgUnitId == null ? "" : "; before org " + beforeOrgUnitId.value()
                        + (beforePositionId == null ? "" : " position " + beforePositionId.value()))
                + (afterOrgUnitId == null ? "" : "; after org " + afterOrgUnitId.value() + (afterPositionId == null ? "" : " position " + afterPositionId.value()))
                + (originalPermissionAdjustmentId == null ? "" : "; regenerates " + originalPermissionAdjustmentId.value())
                + "; escalation due " + escalationDueAt;
    }
}
