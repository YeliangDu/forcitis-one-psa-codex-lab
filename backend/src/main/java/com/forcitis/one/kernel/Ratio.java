package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.util.Objects;

/** Stored with 10 decimals (FD-08); a value with more is refused rather than rounded, so computing never rounds a ratio. */
@ValueObject
public record Ratio(BigDecimal value) {
    public static final int SCALE = 10;

    public Ratio {
        Objects.requireNonNull(value, "value");
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("A ratio keeps at most " + SCALE + " decimals: " + value.toPlainString());
        }
        value = value.setScale(SCALE);
    }

    public static Ratio of(String value) {
        return new Ratio(new BigDecimal(value));
    }
}
