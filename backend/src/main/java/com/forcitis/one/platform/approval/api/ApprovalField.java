package com.forcitis.one.platform.approval.api;

import java.util.List;
import java.util.Objects;

/**
 * A named business field handed to the approval (e.g. the project manager of a timesheet). Assignment rules read
 * these values (RULE-258..260); the owning module has already validated them. Values are textual ids or labels.
 */
public record ApprovalField(String name, List<String> values) {

    public ApprovalField {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("ApprovalField name must not be blank");
        }
        values = List.copyOf(values);
    }
}
