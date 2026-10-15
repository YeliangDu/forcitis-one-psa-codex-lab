package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record SourcePersonId(long value) {

    public SourcePersonId {
        if (value == 0) {
            throw new IllegalArgumentException("SourcePersonId must not be 0");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
