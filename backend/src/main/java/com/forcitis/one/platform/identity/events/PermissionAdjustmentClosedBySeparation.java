package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;

/** An OPEN review item was closed because the person it is about was separated (invariant 29); nothing is granted or taken back. */
public record PermissionAdjustmentClosedBySeparation(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, UserId userId, PermissionAdjustmentKind kind,
                                                     PersonnelCommandId closurePersonnelCommandId) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " closed by separation; user " + userId.value() + "; command " + closurePersonnelCommandId.value();
    }
}
