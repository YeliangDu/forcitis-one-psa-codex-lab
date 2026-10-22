package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Built by the file service; carries no file name, document number or person name (ADR-0060 section 3). */
@ValueObject
public record StoredObjectKey(String value) {

    public StoredObjectKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.startsWith("/") || value.contains("..")) {
            throw new IllegalArgumentException("A stored object key must be a non-blank relative key");
        }
    }
}
