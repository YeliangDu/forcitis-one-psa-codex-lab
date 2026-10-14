package com.forcitis.one.kernel;

/** Typed id of a project. Shared because every fact is attributed to a project (ADR-0016). */
@ValueObject
public record ProjectId(long value) {

    public ProjectId {
        if (value <= 0) {
            throw new IllegalArgumentException("ProjectId must be positive: " + value);
        }
    }
}
