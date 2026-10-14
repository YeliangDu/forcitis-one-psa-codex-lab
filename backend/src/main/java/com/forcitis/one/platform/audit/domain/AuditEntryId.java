package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record AuditEntryId(UUID value) {

    public AuditEntryId {
        Objects.requireNonNull(value, "value");
    }
}
