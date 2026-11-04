package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import org.jspecify.annotations.Nullable;

/** A review item was marked adjusted (adjustedBy is empty when the system did, after a role version dropped the dissolved org unit): the authorizations were changed through an authorization request or a grant that the audit trail shows. */
public record PermissionAdjustmentAdjusted(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, @Nullable UserId adjustedBy, PermissionAdjustmentKind kind,
                                           @Nullable AuthorizationRequestId authorizationRequestId, @Nullable AuditEntryId auditEntryId) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " adjusted"
                + (authorizationRequestId == null ? "" : "; authorization request " + authorizationRequestId.value())
                + (auditEntryId == null ? "" : "; audit entry " + auditEntryId.value());
    }
}
