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
    void hoursCannotBeNegative() {
        assertThatThrownBy(() -> Hours.of("-0.5")).isInstanceOf(IllegalArgumentException.class);
    }
}
