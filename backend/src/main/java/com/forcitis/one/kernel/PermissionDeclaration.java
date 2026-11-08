package com.forcitis.one.kernel;

import java.util.Objects;

/** Immutable module-owned catalogue metadata, including retained retired codes. */
@ValueObject
public record PermissionDeclaration(PermissionCode code, PermissionGrantMode grantMode, boolean deprecated) {
    public PermissionDeclaration {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(grantMode, "grantMode");
    }
}
