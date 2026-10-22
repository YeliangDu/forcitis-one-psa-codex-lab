package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record SealTypeView(SealTypeCode code, String name) {

    public SealTypeView {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A seal type name must not be blank");
        }
    }
}
