package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record NodeKey(String value) {

    public NodeKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeKey must not be blank");
        }
    }
}
