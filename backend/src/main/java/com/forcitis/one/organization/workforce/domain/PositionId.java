package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record PositionId(long value) {
    public PositionId {
        if (value == 0) {
            throw new IllegalArgumentException("PositionId must not be 0");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
