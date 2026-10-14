package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingPeriodTest {

    @Test
    void rejectsMonthsAndYearsOutOfRange() {
        assertThatThrownBy(() -> AccountingPeriod.of(2026, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountingPeriod.of(2026, 13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountingPeriod.of(1899, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountingPeriod.of(10000, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neighboursCrossTheYearBoundary() {
        assertThat(AccountingPeriod.of(2026, 12).next()).isEqualTo(AccountingPeriod.of(2027, 1));
        assertThat(AccountingPeriod.of(2027, 1).previous()).isEqualTo(AccountingPeriod.of(2026, 12));
        assertThat(AccountingPeriod.of(2026, 10).next()).isEqualTo(AccountingPeriod.of(2026, 11));
        assertThat(AccountingPeriod.of(2026, 10).isFollowedBy(AccountingPeriod.of(2026, 11))).isTrue();
        assertThat(AccountingPeriod.of(2026, 10).isFollowedBy(AccountingPeriod.of(2026, 12))).isFalse();
    }

    @Test
    void ordersChronologically() {
        List<AccountingPeriod> sorted = List.of(AccountingPeriod.of(2027, 1), AccountingPeriod.of(2026, 12),
                AccountingPeriod.of(2026, 2)).stream().sorted().toList();
        assertThat(sorted).containsExactly(AccountingPeriod.of(2026, 2), AccountingPeriod.of(2026, 12), AccountingPeriod.of(2027, 1));
        assertThat(AccountingPeriod.of(2026, 2).isBefore(AccountingPeriod.of(2026, 3))).isTrue();
        assertThat(AccountingPeriod.of(2026, 3).isAfter(AccountingPeriod.of(2026, 3))).isFalse();
    }

    @Test
    void derivesFromBusinessDatesAndKnowsItsBounds() {
        AccountingPeriod feb = AccountingPeriod.containing(LocalDate.of(2028, 2, 17));
        assertThat(feb).isEqualTo(AccountingPeriod.of(2028, 2));
        assertThat(feb.firstDay()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(feb.lastDay()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(feb.contains(LocalDate.of(2028, 3, 1))).isFalse();
    }

    @Test
    void roundTripsThroughTheCompactForm() {
        assertThat(AccountingPeriod.of(2026, 3)).hasToString("202603");
        assertThat(AccountingPeriod.parse("202603")).isEqualTo(AccountingPeriod.of(2026, 3));
        assertThatThrownBy(() -> AccountingPeriod.parse("2026-03")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountingPeriod.parse("202613")).isInstanceOf(IllegalArgumentException.class);
    }
}
