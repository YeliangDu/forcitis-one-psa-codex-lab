package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import java.time.Instant;

/** An open review item passed its escalation due time and was marked escalated once; it stays OPEN and nothing is taken back. */
public record PermissionAdjustmentEscalated(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, PermissionAdjustmentKind kind,
                                            Instant escalationDueAt) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " escalated; was due " + escalationDueAt;
    }
}
