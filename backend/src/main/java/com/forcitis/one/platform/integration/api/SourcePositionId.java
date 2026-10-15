package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record SourcePositionId(long value) {

    public SourcePositionId {
        if (value == 0) {
            throw new IllegalArgumentException("SourcePositionId must not be 0");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
