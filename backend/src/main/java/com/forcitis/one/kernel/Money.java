package com.forcitis.one.kernel;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount that has been formed: exactly the currency's minor unit, never rounded here (FD-08). Computing happens in ExactMoney, whose
 * round() is the one place an amount is formed; a total is the sum of formed amounts (FD-07). Cross-currency arithmetic is refused.
 */
@ValueObject
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        int digits = ExactMoney.minorUnit(currency);
        if (amount.stripTrailingZeros().scale() > digits) {
            throw new IllegalArgumentException("Amount " + amount.toPlainString() + " has more decimals than " + currency
                    + " allows: compute in ExactMoney and form the amount with round()");
        }
        amount = amount.setScale(digits);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public ExactMoney exact() {
        return new ExactMoney(amount, currency);
    }

    public Money plus(Money other) {
        return new Money(amount.add(sameCurrency(other).amount), currency);
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(sameCurrency(other).amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public int compareTo(Money other) {
        return amount.compareTo(sameCurrency(other).amount);
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
        return other;
    }
}
