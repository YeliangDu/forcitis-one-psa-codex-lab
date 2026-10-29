package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    static final Currency CNY = Currency.getInstance("CNY");

    @Test
    void equalityIgnoresScale() {
        assertThat(Money.of("1.0", "CNY")).isEqualTo(Money.of("1.00", "CNY"))
                .hasSameHashCodeAs(Money.of("1", "CNY"));
        assertThat(ExactMoney.of("1.50", "CNY")).isEqualTo(ExactMoney.of("1.5", "CNY"))
                .hasSameHashCodeAs(ExactMoney.of("1.500000", "CNY"));
    }

    @Test
    @DisplayName("FD-08 an amount is formed once: Money refuses more decimals than the currency has instead of rounding them away")
    void moneyNeverRoundsOnItsOwn() {
        assertThatThrownBy(() -> Money.of("10.005", "CNY")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ExactMoney");
        assertThat(Money.of("10.01", "CNY").amount()).isEqualByComparingTo("10.01");
    }

    @Test
    @DisplayName("FD-02 FD-08 computing does not round; rounding happens once, half up to the currency's minor unit, when the amount is formed")
    void roundsOnceWhenTheAmountIsFormed() {
        ExactMoney half = Money.of("0.05", "CNY").exact().times(Ratio.of("0.5"));
        assertThat(half.times(Ratio.of("0.5")).rounded()).isEqualTo(Money.of("0.01", "CNY"));
        assertThat(half.rounded().exact().times(Ratio.of("0.5")).rounded()).isEqualTo(Money.of("0.02", "CNY"));
        assertThat(ExactMoney.of("10.005", "CNY").rounded()).isEqualTo(Money.of("10.01", "CNY"));
        assertThat(ExactMoney.of("-10.005", "CNY").rounded()).isEqualTo(Money.of("-10.01", "CNY"));
        assertThat(Money.of("10.00", "CNY").exact().times(Rate.of("0.0625")).rounded()).isEqualTo(Money.of("0.63", "CNY"));
    }

    @Test
    @DisplayName("FD-07 a total is the sum of the lines after each line is formed, not the rounded sum of the unrounded lines")
    void totalIsTheSumOfFormedLines() {
        ExactMoney line = ExactMoney.of("0.333", "CNY");
        Money total = line.rounded().plus(line.rounded()).plus(line.rounded());
        assertThat(total).isEqualTo(Money.of("0.99", "CNY"));
        assertThat(line.plus(line).plus(line).rounded()).isEqualTo(Money.of("1.00", "CNY"));
    }

    @Test
    @DisplayName("FD-08 a stored rate keeps 6 decimals and a ratio 10; a value that would lose digits is refused, not rounded")
    void ratesAndRatiosKeepTheirDecimals() {
        assertThat(Rate.of("0.06").value().scale()).isEqualTo(6);
        assertThat(Rate.of("0.123456").value()).isEqualByComparingTo("0.123456");
        assertThatThrownBy(() -> Rate.of("0.1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Ratio.of("0.3333333333").value().scale()).isEqualTo(10);
        assertThatThrownBy(() -> Ratio.of("0.33333333333")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Rate.of("0.060")).isEqualTo(Rate.of("0.06"));
    }

    @Test
    void refusesMixedCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "CNY").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Currency mismatch");
        assertThatThrownBy(() -> ExactMoney.of("1", "CNY").plus(ExactMoney.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Currency mismatch");
    }

    @Test
    void refusesMissingCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExactMoney(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
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
        assertThat(Money.of("0", "CNY").isZero()).isTrue();
        assertThat(Money.of("0.01", "CNY").isPositive()).isTrue();
        assertThat(Money.zero(Currency.getInstance("USD")).isZero()).isTrue();
        assertThat(ExactMoney.of("0.001", "CNY").rounded().isZero()).isTrue();
        assertThat(ExactMoney.of("3", "CNY").minus(ExactMoney.of("5", "CNY")).negate()).isEqualTo(ExactMoney.of("2", "CNY"));
    }

    @Test
    void refusesMissingAmount() {
        assertThatThrownBy(() -> new Money(null, CNY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExactMoney(null, CNY)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void hoursCannotBeNegative() {
        assertThatThrownBy(() -> Hours.of("-0.5")).isInstanceOf(IllegalArgumentException.class);
    }
}
