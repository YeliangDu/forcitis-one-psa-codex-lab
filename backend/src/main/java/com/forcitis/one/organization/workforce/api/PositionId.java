package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record PositionId(UUID value) {

    public PositionId {
        value = Uuids.requireKey(value, "PositionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
