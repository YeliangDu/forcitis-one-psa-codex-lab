package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.kernel.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One bonus input as payroll readers see it. bonus and reasonKey are SENSITIVE personal information (PY3 record): never log or publish them. */
public record BonusInputSummary(BonusInputId id, EmploymentRelationshipId employmentRelationshipId, LegalEntityId payingLegalEntityId,
                                LocalDate payrollMonthStartDate, Money bonus, String reasonKey, BonusInputStatus status,
                                EmployeeId enteredByEmployeeId, @Nullable UserId voidedByUserId) {

    /** The amount alone, for readers that keep currency apart. */
    public BigDecimal bonusAmount() {
        return bonus.amount();
    }

    public String bonusCurrency() {
        return bonus.currency().getCurrencyCode();
    }
}
