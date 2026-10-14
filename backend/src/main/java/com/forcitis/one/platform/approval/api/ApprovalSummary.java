package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** What the business module wants an approver to see about the document; cards never look the document up again. */
@ValueObject
public record ApprovalSummary(String value) {
    public static final int MAX_LENGTH = 200;

    public ApprovalSummary {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("An approval summary is 1.." + MAX_LENGTH + " characters");
        }
    }
}
