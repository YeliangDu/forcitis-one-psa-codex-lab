package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.ValueObject;

/** Typed id of one sync run. */
@ValueObject
public record SyncRunId(long value) {

    public SyncRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("SyncRunId must be positive");
        }
    }
}
