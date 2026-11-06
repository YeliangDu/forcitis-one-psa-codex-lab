package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.organization.payroll.api.PayrollRuleKind;

/** The row kinds of ck_payroll_rule_rows_row_kind, each belonging to one category of version. */
public enum PayrollRuleRowKind {
    INCOME_TAX_BAND(PayrollRuleKind.INCOME_TAX),
    INCOME_TAX_MONTHLY_BASIC_DEDUCTION(PayrollRuleKind.INCOME_TAX),
    SOCIAL_INSURANCE_RATE(PayrollRuleKind.SOCIAL_INSURANCE),
    SOCIAL_INSURANCE_BASE_LIMIT(PayrollRuleKind.SOCIAL_INSURANCE),
    HOUSING_FUND_RATE(PayrollRuleKind.HOUSING_FUND),
    HOUSING_FUND_BASE_LIMIT(PayrollRuleKind.HOUSING_FUND),
    ROUNDING_MODE(PayrollRuleKind.ROUNDING);

    private final PayrollRuleKind ruleKind;

    PayrollRuleRowKind(PayrollRuleKind ruleKind) {
        this.ruleKind = ruleKind;
    }

    public PayrollRuleKind ruleKind() {
        return ruleKind;
    }

    /** Kinds that exist once per version: more than one row of them has no meaning. */
    boolean single() {
        return this == INCOME_TAX_MONTHLY_BASIC_DEDUCTION || this == ROUNDING_MODE;
    }
}
