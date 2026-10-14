package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Process instance id in the OLD system's engine (text, opaque to the new system). */
@ValueObject
public record LegacyInstanceId(String value) {

    public LegacyInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LegacyInstanceId must not be blank");
        }
    }
}
