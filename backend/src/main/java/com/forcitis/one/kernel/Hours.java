package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@ValueObject
public record Hours(BigDecimal value) {
    public static final Hours ZERO = new Hours(BigDecimal.ZERO);

    public Hours {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Hours must not be negative: " + value);
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
    }

    public static Hours of(String value) {
        return new Hours(new BigDecimal(value));
    }

    public Hours plus(Hours other) {
        return new Hours(value.add(other.value));
    }

    public boolean exceeds(Hours other) {
        return value.compareTo(other.value) > 0;
    }
}
