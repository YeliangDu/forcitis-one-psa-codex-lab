package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;

/** Time-ordered, so the order of the lines of the trail and the order of their ids agree. */
@ValueObject
public record TaskRecordId(UUID value) {

    public TaskRecordId {
        Objects.requireNonNull(value, "value");
    }
}
