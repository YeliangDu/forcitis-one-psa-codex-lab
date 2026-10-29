package com.forcitis.one.kernel;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

@ValueObject
public record Reason(String value) {

    public Reason {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
    }

    public static @Nullable Reason ofNullable(@Nullable String text) {
        return text == null || text.isBlank() ? null : new Reason(text);
    }
}
