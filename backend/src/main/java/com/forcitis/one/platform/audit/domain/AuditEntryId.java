package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record AuditEntryId(UUID value) {

    public AuditEntryId {
        value = Uuids.requireKey(value, "AuditEntryId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
