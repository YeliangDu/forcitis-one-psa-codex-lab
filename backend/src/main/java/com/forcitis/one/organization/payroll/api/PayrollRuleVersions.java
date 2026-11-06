package com.forcitis.one.organization.payroll.api;

import java.time.LocalDate;

/**
 * The rule version in force for a category and a jurisdiction on a date. Fails with
 * {@link PayrollError#ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION} when none covers the date: no default rule exists, a made-up rate would be wrong silently.
 */
public interface PayrollRuleVersions {

    EffectivePayrollRuleVersion effectiveOn(PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey, LocalDate date);
}
