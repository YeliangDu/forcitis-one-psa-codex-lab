package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Role a person plays on a project (e.g. project manager); the third dimension of an {@link AccessScope}. */
@ValueObject
public record ProjectRole(String value) {

    public ProjectRole {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProjectRole must not be blank");
        }
    }
}
