package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record DocumentId(long value) {
    public DocumentId {
        if (value <= 0) {
            throw new IllegalArgumentException("DocumentId must be positive: " + value);
        }
    }
}
