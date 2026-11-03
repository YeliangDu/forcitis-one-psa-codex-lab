package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.audit.api.AuditDetail;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import java.time.Instant;

/** An upheld review item reached its expiry and the daily job turned it EXPIRED; nothing was granted or taken back. expiredAt is the job's clock, not the expiry. */
public record PermissionAdjustmentExpired(EventEnvelope envelope, PermissionAdjustmentId permissionAdjustmentId, PermissionAdjustmentKind kind,
                                          Instant upholdExpiresAt, Instant expiredAt, boolean heldAtExpiry) implements DomainEvent, AuditDetail {

    @Override
    public String auditDetail() {
        return "item " + permissionAdjustmentId.value() + " " + kind + " expired at " + expiredAt + "; was valid until " + upholdExpiresAt
                + (heldAtExpiry ? "; the person's separation was held" : "");
    }
}
