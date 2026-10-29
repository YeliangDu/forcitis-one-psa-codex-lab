package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.util.Objects;

/** Stored with 6 decimals (FD-08); a value with more is refused rather than rounded, so computing never rounds a rate. */
@ValueObject
public record Rate(BigDecimal value) {
    public static final int SCALE = 6;

    public Rate {
        Objects.requireNonNull(value, "value");
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("A rate keeps at most " + SCALE + " decimals: " + value.toPlainString());
        }
        value = value.setScale(SCALE);
    }

    public static Rate of(String value) {
        return new Rate(new BigDecimal(value));
    }
}
