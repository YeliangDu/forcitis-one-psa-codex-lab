package com.forcitis.one.kernel;

/** Typed id of a legal entity (the book of account owner). Shared by numbering, periods and data scope. */
@ValueObject
public record LegalEntityId(long value) {

    public LegalEntityId {
        if (value <= 0) {
            throw new IllegalArgumentException("LegalEntityId must be positive: " + value);
        }
    }
}
