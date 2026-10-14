package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;

/** Typed id of a role. */
@ValueObject
public record RoleId(long value) {

    public RoleId {
        if (value <= 0) {
            throw new IllegalArgumentException("RoleId must be positive");
        }
    }
}
