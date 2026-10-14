package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record ProjectRole(String value) {

    public ProjectRole {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProjectRole must not be blank");
        }
    }
}
