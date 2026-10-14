package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record ProcessName(String value) {

    public ProcessName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProcessName must not be blank");
        }
    }
}
