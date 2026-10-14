package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** A switch the requesting module computed and the process definition reads to choose its route. */
@ValueObject
public record RoutingFlag(String name) {

    public RoutingFlag {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A routing flag name must not be blank");
        }
    }
}
