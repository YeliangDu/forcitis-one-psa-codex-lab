package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record AttributionId(UUID value) {
    public AttributionId {
        Objects.requireNonNull(value, "value");
    }
}
