package com.forcitis.one.kernel;

@ValueObject
public record ProjectId(long value) {
    public ProjectId {
        if (value <= 0) {
            throw new IllegalArgumentException("ProjectId must be positive: " + value);
        }
    }
}
