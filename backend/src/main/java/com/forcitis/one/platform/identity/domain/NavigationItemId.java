package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record NavigationItemId(UUID value) {

    public NavigationItemId {
        Objects.requireNonNull(value, "value");
    }
}
