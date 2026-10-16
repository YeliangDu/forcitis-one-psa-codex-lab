package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** The number, the counter whose row the same transaction changed (so that the caller can name it as a changed aggregate), and the template version it was issued under. */
@ValueObject
public record IssuedNumber(DocumentNumber number, NumberingCounterId counterId, NumberingRuleVersionId ruleVersionId) {

    public IssuedNumber {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(counterId, "counterId");
        Objects.requireNonNull(ruleVersionId, "ruleVersionId");
    }

    public String value() {
        return number.value();
    }
}
