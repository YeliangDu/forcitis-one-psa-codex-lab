package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record NavigationItemId(long value) {

    public NavigationItemId {
        if (value <= 0) {
            throw new IllegalArgumentException("NavigationItemId must be positive");
        }
    }
}
