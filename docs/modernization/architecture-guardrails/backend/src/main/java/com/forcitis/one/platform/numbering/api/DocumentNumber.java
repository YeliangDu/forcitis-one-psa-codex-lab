package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record DocumentNumber(String value) {

    public DocumentNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException("Document number must be 1..64 characters");
        }
    }
}
