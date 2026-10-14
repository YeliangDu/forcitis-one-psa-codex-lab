package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Amount + currency (ADR-0001 hard constraint 1). Always normalised to 2 decimals HALF_UP, so equals/hashCode
 * are scale-safe (1.0 CNY == 1.00 CNY). Arithmetic across currencies is refused.
 */
@ValueObject
public record Money(BigDecimal amount, Currency currency) {

    public static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        return new Money(amount.add(sameCurrency(other).amount), currency);
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(sameCurrency(other).amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(Objects.requireNonNull(factor, "factor")), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
        return other;
    }
}
