package com.forcitis.one.kernel;

@ValueObject
public record LegalEntityId(long value) {
    public LegalEntityId {
        if (value <= 0) {
            throw new IllegalArgumentException("LegalEntityId must be positive: " + value);
        }
    }
}
