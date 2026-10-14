package com.forcitis.one.kernel;

/** Typed id of a node in a project's work breakdown structure. */
@ValueObject
public record WbsNodeId(long value) {

    public WbsNodeId {
        if (value <= 0) {
            throw new IllegalArgumentException("WbsNodeId must be positive: " + value);
        }
    }
}
