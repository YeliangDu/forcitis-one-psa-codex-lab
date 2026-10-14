package com.forcitis.one.kernel;

import java.util.Objects;
import java.util.UUID;

@ValueObject
public record ProjectId(UUID value) {

    public ProjectId {
        Objects.requireNonNull(value, "value");
    }
}
