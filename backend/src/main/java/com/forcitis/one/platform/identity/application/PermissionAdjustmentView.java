package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What a reader sees of an item: who or what it is about, its state and, once upheld, the business owner and the expiry. No permission codes, no scope, no names (ADR-0032 decision 4 item 3). */
public record PermissionAdjustmentView(UUID permissionAdjustmentId, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin originKind,
                                       PermissionAdjustmentStatus status, @Nullable UUID userId, @Nullable UUID roleId, @Nullable UUID orgUnitId,
                                       @Nullable UUID businessOwnerEmployeeId, @Nullable Instant upholdExpiresAt, long version) {

    static PermissionAdjustmentView of(PermissionAdjustment item) {
        return new PermissionAdjustmentView(item.id().value(), item.kind(), item.origin(), item.status(), item.user() == null ? null : item.user().value(),
                item.role() == null ? null : item.role().value(), item.inactiveOrgUnit() == null ? null : item.inactiveOrgUnit().value(),
                item.businessOwner() == null ? null : item.businessOwner().value(), item.upholdExpiresAt(), item.version());
    }
}
