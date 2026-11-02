package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record AccountRoleGrantId(UUID value) {

    public AccountRoleGrantId {
        value = Uuids.requireKey(value, "AccountRoleGrantId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
