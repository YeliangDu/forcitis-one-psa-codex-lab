package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
/** The old system's process instance key; it stays inside platform.integration (ADR-0033 guard 3). */
public record LegacyInstanceId(String value) {

    public LegacyInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LegacyInstanceId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
