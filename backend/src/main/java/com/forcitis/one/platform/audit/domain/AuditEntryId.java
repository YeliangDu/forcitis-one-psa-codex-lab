package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record AuditEntryId(long value) {

    public AuditEntryId {
        if (value <= 0) {
            throw new IllegalArgumentException("AuditEntryId must be positive: " + value);
        }
    }
}
