package com.forcitis.one.kernel;

import java.util.Objects;
import java.util.UUID;

@ValueObject
public record WbsNodeId(UUID value) {

    public WbsNodeId {
        Objects.requireNonNull(value, "value");
    }
}
