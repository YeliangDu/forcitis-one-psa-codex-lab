package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record AuthorizationRequestId(UUID value) {

    public AuthorizationRequestId {
        value = Uuids.requireKey(value, "AuthorizationRequestId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
