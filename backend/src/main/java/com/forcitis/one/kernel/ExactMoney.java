package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount being computed: full precision, never rounded during computation (FD-08). round() forms the Money, half up to the
 * currency's minor unit (FD-02); it is the only rounding of amounts in the system. Trailing zeros are dropped so equals is scale-safe.
 */
@ValueObject
public record ExactMoney(BigDecimal amount, Currency currency) {

    public ExactMoney {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.stripTrailingZeros();
    }

    public static ExactMoney of(String amount, String currencyCode) {
        return new ExactMoney(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public Money round() {
        return new Money(amount.setScale(minorUnit(currency), RoundingMode.HALF_UP), currency);
    }

    public ExactMoney plus(ExactMoney other) {
        return new ExactMoney(amount.add(sameCurrency(other).amount), currency);
    }

    public ExactMoney minus(ExactMoney other) {
        return new ExactMoney(amount.subtract(sameCurrency(other).amount), currency);
    }

    public ExactMoney negate() {
        return new ExactMoney(amount.negate(), currency);
    }

    public ExactMoney times(Rate rate) {
        return new ExactMoney(amount.multiply(Objects.requireNonNull(rate, "rate").value()), currency);
    }

    public ExactMoney times(Ratio ratio) {
        return new ExactMoney(amount.multiply(Objects.requireNonNull(ratio, "ratio").value()), currency);
    }

    static int minorUnit(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException(currency + " has no minor unit, so it cannot carry an amount");
        }
        return digits;
    }

    private ExactMoney sameCurrency(ExactMoney other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
        return other;
    }
}
