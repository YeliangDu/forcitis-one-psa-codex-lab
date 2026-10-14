package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record ApprovalInstanceId(String value) {

    public ApprovalInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ApprovalInstanceId must not be blank");
        }
    }
}
