package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record ApprovalTaskId(String value) {

    public ApprovalTaskId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ApprovalTaskId must not be blank");
        }
    }
}
