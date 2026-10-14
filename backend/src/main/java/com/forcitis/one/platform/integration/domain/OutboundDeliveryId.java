package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

@ValueObject
public record OutboundDeliveryId(UUID value) {

    public OutboundDeliveryId {
        Objects.requireNonNull(value, "value");
    }
}
