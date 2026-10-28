package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One held separation (ADR-0068 item 2.1, separation_holds.separation_hold_id): a UUIDv7 the workforce application makes. */
@ValueObject
public record SeparationHoldId(UUID value) {

    public SeparationHoldId {
        value = Uuids.requireKey(value, "SeparationHoldId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
