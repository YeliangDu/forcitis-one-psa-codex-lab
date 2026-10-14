package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.ValueObject;

/** Typed id of a position; same value as the organisation source (signed bigint). */
@ValueObject
public record PositionId(long value) {

    public PositionId {
        if (value == 0) {
            throw new IllegalArgumentException("PositionId must not be 0");
        }
    }
}
