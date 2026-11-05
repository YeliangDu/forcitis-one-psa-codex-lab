package com.forcitis.one.organization.payroll.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Money;
import com.forcitis.one.organization.payroll.api.PayrollError;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BonusDeterminationTest {

    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);

    private static void refused(LocalDate month, String amount, String currency, String reason) {
        assertThatThrownBy(() -> BonusDetermination.check(month, Money.of(amount, currency), reason)).isInstanceOfSatisfying(DomainException.class,
                e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(PayrollError.ORGANIZATION_PAYROLL_BONUS_INPUT_INVALID));
    }

    @Test
    @DisplayName("#1436 PY3 entry rules: first-of-month, positive, at most 17 integer digits, CNY, capital-letter reason key")
    void theEntryRulesRefuseWhatThePy3RecordRefuses() {
        assertThatCode(() -> BonusDetermination.check(MONTH, Money.of("1500.50", "CNY"), "YEAR_END")).doesNotThrowAnyException();
        refused(LocalDate.of(2026, 10, 2), "1500.50", "CNY", "YEAR_END");
        refused(MONTH, "0.00", "CNY", "YEAR_END");
        refused(MONTH, "-1.00", "CNY", "YEAR_END");
        refused(MONTH, "9".repeat(18) + ".00", "CNY", "YEAR_END");
        refused(MONTH, "1.00", "USD", "YEAR_END");
        refused(MONTH, "1.00", "CNY", "year_end");
        refused(MONTH, "1.00", "CNY", "Y");
    }
}
