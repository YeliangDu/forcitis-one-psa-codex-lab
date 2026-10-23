package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record SealTypeCode(String value) {

    public SealTypeCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A seal type code must not be blank");
        }
    }
}
