package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Roles are data of platform.identity; no module spells a role code in code. */
@ValueObject
public record RoleCode(String value) {

    public RoleCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RoleCode must not be blank");
        }
    }
}
