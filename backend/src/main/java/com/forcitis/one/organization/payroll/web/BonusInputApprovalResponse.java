package com.forcitis.one.organization.payroll.web;

import com.forcitis.one.organization.payroll.application.BonusInputApprovalView;
import java.time.LocalDate;
import java.util.UUID;

/** API money is {amount: decimal string, currency: ISO 4217} (XD-01). */
record BonusInputApprovalResponse(UUID employmentRelationshipId, LocalDate payrollMonthStartDate, Amount amount, String reasonKey) {

    record Amount(String amount, String currency) {
    }

    static BonusInputApprovalResponse of(BonusInputApprovalView view) {
        return new BonusInputApprovalResponse(view.employmentRelationshipId().value(), view.payrollMonthStartDate(),
                new Amount(view.bonus().amount().toPlainString(), view.bonus().currency().getCurrencyCode()), view.reasonKey());
    }
}
