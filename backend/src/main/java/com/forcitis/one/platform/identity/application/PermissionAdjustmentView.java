package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a reader sees of an item: who or what it is about (id and display name), what triggered it, its state and, once upheld, the business owner and the
 * expiry. A name is null when the id has no row to name it. No permission codes, no scope (ADR-0032 decision 4 item 3 lets the reviewer see names of the
 * person, department and role).
 */
public record PermissionAdjustmentView(UUID permissionAdjustmentId, PermissionAdjustmentKind kind, PermissionAdjustmentOrigin originKind,
                                       PermissionAdjustmentStatus status, @Nullable UUID userId, @Nullable UUID roleId,
                                       @Nullable String roleName, @Nullable UUID orgUnitId, @Nullable String orgUnitName,
                                       @Nullable UUID businessOwnerEmployeeId, @Nullable UUID triggerPersonnelCommandId,
                                       @Nullable UUID triggerOrgStructureCommandId, @Nullable TriggerFieldKind triggerField, @Nullable Instant upholdExpiresAt,
                                       long version) {
}
