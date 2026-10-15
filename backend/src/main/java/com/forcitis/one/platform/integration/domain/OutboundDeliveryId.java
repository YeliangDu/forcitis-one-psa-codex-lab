package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record OutboundDeliveryId(UUID value) {

    public OutboundDeliveryId {
        value = Uuids.requireKey(value, "OutboundDeliveryId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
