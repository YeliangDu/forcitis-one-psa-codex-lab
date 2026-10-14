package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.List;
import java.util.Objects;

@ValueObject
public record ApprovalField(String name, List<String> values) {

    public ApprovalField {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ApprovalField name must not be blank");
        }
        values = List.copyOf(values);
    }
}
