package com.forcitis.one.kernel;

import java.util.Objects;
import java.util.UUID;

@ValueObject
public record LegalEntityId(UUID value) {

    public LegalEntityId {
        Objects.requireNonNull(value, "value");
    }
}
