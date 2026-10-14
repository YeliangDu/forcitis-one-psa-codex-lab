package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record SyncRunId(long value) {
    public SyncRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("SyncRunId must be positive");
        }
    }
}
