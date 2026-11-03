package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record PermissionAdjustmentId(UUID value) {

    public PermissionAdjustmentId {
        value = Uuids.requireKey(value, "PermissionAdjustmentId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
