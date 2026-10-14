package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record LegacyInstanceId(String value) {

    public LegacyInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LegacyInstanceId must not be blank");
        }
    }
}
