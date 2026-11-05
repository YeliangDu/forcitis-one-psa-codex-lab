package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** One bonus input as payroll readers see it. bonusAmount and reasonKey are SENSITIVE personal information (PY3 record): never log or publish them. */
public record BonusInputSummary(BonusInputId id, EmploymentRelationshipId employmentRelationshipId, LegalEntityId payingLegalEntityId,
                                LocalDate payrollMonthStartDate, BigDecimal bonusAmount, String bonusCurrency, String reasonKey, BonusInputStatus status,
                                EmployeeId enteredByEmployeeId, @Nullable UserId voidedByUserId) {
}
