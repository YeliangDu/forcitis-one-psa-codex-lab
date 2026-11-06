package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.kernel.Money;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.time.LocalDate;

/** What an approver of a bonus input is shown. money and reasonKey are SENSITIVE personal information (PY3 record): never log or publish them. */
public record BonusInputApprovalView(EmploymentRelationshipId employmentRelationshipId, LocalDate payrollMonthStartDate, Money bonus, String reasonKey) {
}
