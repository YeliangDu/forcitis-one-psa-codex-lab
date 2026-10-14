package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Code of a role, e.g. "BU-002" (department director, RULE-256). */
@ValueObject
public record RoleCode(String value) {

    public RoleCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RoleCode must not be blank");
        }
    }
}
