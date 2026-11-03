package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.Reasoned;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A review item was upheld: the person's authorizations stay as they are; the handler's reason goes to the audit entry's change reason, not its detail. Only a sensitive-role review names a business owner and an expiry. */
public record PermissionAdjustmentUpheld(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, UserId upheldBy, PermissionAdjustmentKind kind,
                                         @Nullable EmployeeId businessOwnerEmployeeId, @Nullable Instant upholdExpiresAt, Reason reason) implements DomainEvent, AuditDetail, Reasoned {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " upheld"
                + (businessOwnerEmployeeId == null ? "" : "; business owner " + businessOwnerEmployeeId.value())
                + (upholdExpiresAt == null ? "" : "; expires " + upholdExpiresAt);
    }
}
