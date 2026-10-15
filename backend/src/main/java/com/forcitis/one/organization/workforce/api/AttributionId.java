package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record AttributionId(UUID value) {
    public AttributionId {
        value = Uuids.requireKey(value, "AttributionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
