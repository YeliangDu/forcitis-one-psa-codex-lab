package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.application.PermissionAdjustmentView;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** businessOwnerEmployeeId and upholdExpiresAt are set only on an upheld sensitive-role review. */
record PermissionAdjustmentResponse(UUID permissionAdjustmentId, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin originKind, String status,
                                    @Nullable UUID userId, @Nullable UUID roleId, @Nullable UUID orgUnitId, @Nullable UUID businessOwnerEmployeeId,
                                    @Nullable Instant upholdExpiresAt) {

    static PermissionAdjustmentResponse of(PermissionAdjustmentView view) {
        return new PermissionAdjustmentResponse(view.permissionAdjustmentId(), view.kind(), view.originKind(), view.status().name(), view.userId(),
                view.roleId(), view.orgUnitId(), view.businessOwnerEmployeeId(), view.upholdExpiresAt());
    }
}
