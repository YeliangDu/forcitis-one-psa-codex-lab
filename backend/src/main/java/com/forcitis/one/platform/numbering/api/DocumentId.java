package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;

/** Id of a document in the new system, whatever its type; paired with a {@link DocumentType} wherever it is looked up. */
@ValueObject
public record DocumentId(long value) {

    public DocumentId {
        if (value <= 0) {
            throw new IllegalArgumentException("DocumentId must be positive: " + value);
        }
    }
}
