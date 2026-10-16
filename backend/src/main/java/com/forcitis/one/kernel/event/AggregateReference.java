package com.forcitis.one.kernel.event;

import java.util.Objects;
import java.util.UUID;

public record AggregateReference(String aggregateType, UUID aggregateId) {

    public AggregateReference {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
    }
}
