package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record SourceUnitId(long value) {

    public SourceUnitId {
        if (value == 0) {
            throw new IllegalArgumentException("SourceUnitId must not be 0");
        }
    }
}
