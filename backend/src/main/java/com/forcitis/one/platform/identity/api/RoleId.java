package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record RoleId(UUID value) {

    public RoleId {
        Objects.requireNonNull(value, "value");
    }
}
