package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record OutboundDeliveryId(long value) {

    public OutboundDeliveryId {
        if (value <= 0) {
            throw new IllegalArgumentException("OutboundDeliveryId must be positive: " + value);
        }
    }
}
