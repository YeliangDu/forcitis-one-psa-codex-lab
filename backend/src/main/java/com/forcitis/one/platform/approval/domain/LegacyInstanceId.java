package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Opaque text id from the OLD system's engine. */
@ValueObject
public record LegacyInstanceId(String value) {

    public LegacyInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LegacyInstanceId must not be blank");
        }
    }
}
