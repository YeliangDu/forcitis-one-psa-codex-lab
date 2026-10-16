package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

/** The number and the counter whose row the same transaction changed, so that the caller can name it as a changed aggregate. */
@ValueObject
public record IssuedNumber(DocumentNumber number, NumberingCounterId counterId) {

    public IssuedNumber {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(counterId, "counterId");
    }

    public String value() {
        return number.value();
    }
}
