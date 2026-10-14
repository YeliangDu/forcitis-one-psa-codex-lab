package com.forcitis.one.kernel.event;

import java.util.Objects;

public record AggregateReference(String aggregateType, String aggregateId) {

    public AggregateReference {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
    }
}
