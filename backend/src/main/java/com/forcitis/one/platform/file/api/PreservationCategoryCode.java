package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record PreservationCategoryCode(String value) {

    public PreservationCategoryCode {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A preservation category code must not be blank");
        }
    }
}
