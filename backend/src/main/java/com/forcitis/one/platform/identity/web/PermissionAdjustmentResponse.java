package com.forcitis.one.platform.identity.web;

import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import com.forcitis.one.platform.identity.application.PermissionAdjustmentView;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * businessOwnerEmployeeId and upholdExpiresAt are set only on an upheld sensitive-role review. A name is null when its id has no row. The trigger ids say
 * which command opened the item: a personnel command (with triggerField for an assignment change), an org-structure command, or neither.
 */
record PermissionAdjustmentResponse(UUID permissionAdjustmentId, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin originKind,
                                    PermissionAdjustmentStatus status, @Nullable UUID userId, @Nullable String userName, @Nullable UUID roleId,
                                    @Nullable String roleName, @Nullable UUID orgUnitId, @Nullable String orgUnitName,
                                    @Nullable UUID businessOwnerEmployeeId, @Nullable String businessOwnerName, @Nullable UUID triggerPersonnelCommandId,
                                    @Nullable UUID triggerOrgStructureCommandId, @Nullable TriggerFieldKind triggerField, @Nullable Instant upholdExpiresAt) {

    static PermissionAdjustmentResponse of(PermissionAdjustmentView view) {
        return new PermissionAdjustmentResponse(view.permissionAdjustmentId(), view.kind(), view.originKind(), view.status(), view.userId(), view.userName(),
                view.roleId(), view.roleName(), view.orgUnitId(), view.orgUnitName(), view.businessOwnerEmployeeId(), view.businessOwnerName(),
                view.triggerPersonnelCommandId(), view.triggerOrgStructureCommandId(), view.triggerField(), view.upholdExpiresAt());
    }
}
