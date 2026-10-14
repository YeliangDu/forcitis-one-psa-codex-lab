package com.forcitis.one.kernel;

import java.util.Objects;

@ValueObject
public record DocumentId(String value) {

    public DocumentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DocumentId must not be blank");
        }
    }
}
