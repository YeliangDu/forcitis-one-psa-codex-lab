package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** Fingerprint of the content to approve: a repeat approver passes automatically only while it is unchanged (RULE-302). */
@ValueObject
public record ContentHash(String value) {

    public ContentHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A content hash must not be blank");
        }
    }
}
