package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record NodeName(String value) {

    public NodeName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeName must not be blank");
        }
    }
}
