package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record RoleId(UUID value) {

    public RoleId {
        value = Uuids.requireKey(value, "RoleId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
