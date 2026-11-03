package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One request for the finance head of a legal entity to countersign (ADR-0073 item 3.6): a UUIDv7 the workforce application makes through kernel.IdGenerator. */
@ValueObject
public record AttributionCountersignId(UUID value) {

    public AttributionCountersignId {
        value = Uuids.requireKey(value, "AttributionCountersignId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
