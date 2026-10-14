package com.forcitis.one.delivery.execution.api;

import com.forcitis.one.kernel.ValueObject;

/** Typed id; lives in api because other modules refer to task packages. */
@ValueObject
public record WorkPackageId(long value) {

    public WorkPackageId {
        if (value <= 0) {
            throw new IllegalArgumentException("WorkPackageId must be positive: " + value);
        }
    }
}
