package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void equalityIgnoresScale() {
        assertThat(Money.of("1.0", "CNY")).isEqualTo(Money.of("1.00", "CNY"))
                .hasSameHashCodeAs(Money.of("1", "CNY"));
    }

    @Test
    void roundsHalfUpToCents() {
        assertThat(Money.of("10.005", "CNY").amount()).isEqualByComparingTo("10.01");
    }

    @Test
    void refusesMixedCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "CNY").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Currency mismatch");
    }

    @Test
    void refusesMissingCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void comparesWithinOneCurrencyOnly() {
        assertThat(Money.of("1.50", "CNY").compareTo(Money.of("1.5", "CNY"))).isZero();
        assertThat(Money.of("2", "CNY").compareTo(Money.of("1", "CNY"))).isPositive();
        assertThatThrownBy(() -> Money.of("1", "CNY").compareTo(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Currency mismatch");
    }

    @Test
    void signHelpersAndNegation() {
        assertThat(Money.of("3", "CNY").negate()).isEqualTo(Money.of("-3", "CNY"));
        assertThat(Money.of("-3", "CNY").isNegative()).isTrue();
        assertThat(Money.of("0.001", "CNY").isZero()).isTrue();
        assertThat(Money.of("0.01", "CNY").isPositive()).isTrue();
        assertThat(Money.zero(java.util.Currency.getInstance("USD")).isZero()).isTrue();
    }

    @Test
    void multiplicationRoundsOnceAtTheEnd() {
        assertThat(Money.of("10.00", "CNY").times(new BigDecimal("0.0625"))).isEqualTo(Money.of("0.63", "CNY"));
    }

    @Test
    void refusesMissingAmount() {
        assertThatThrownBy(() -> new Money(null, java.util.Currency.getInstance("CNY"))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void hoursCannotBeNegative() {
        assertThatThrownBy(() -> Hours.of("-0.5")).isInstanceOf(IllegalArgumentException.class);
    }
}
