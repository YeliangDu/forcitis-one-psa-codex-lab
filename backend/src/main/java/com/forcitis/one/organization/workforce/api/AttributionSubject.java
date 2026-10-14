package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record AttributionSubject(AttributionKind kind, long subjectKey) {
    public AttributionSubject {
        Objects.requireNonNull(kind, "kind");
        if (subjectKey == 0) {
            throw new IllegalArgumentException("subjectKey must not be 0");
        }
    }
}
