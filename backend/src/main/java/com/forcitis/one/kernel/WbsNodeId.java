package com.forcitis.one.kernel;

@ValueObject
public record WbsNodeId(long value) {
    public WbsNodeId {
        if (value <= 0) {
            throw new IllegalArgumentException("WbsNodeId must be positive: " + value);
        }
    }
}
