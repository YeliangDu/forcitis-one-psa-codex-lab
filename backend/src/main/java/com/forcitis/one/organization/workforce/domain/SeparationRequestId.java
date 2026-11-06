package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** A separation request of one employment relationship: a UUIDv7 the workforce application makes through kernel.IdGenerator. */
@ValueObject
public record SeparationRequestId(UUID value) {

    public SeparationRequestId {
        value = Uuids.requireKey(value, "SeparationRequestId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
