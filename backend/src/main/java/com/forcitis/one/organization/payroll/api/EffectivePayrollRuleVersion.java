package com.forcitis.one.organization.payroll.api;

import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The version in force on a date; effectiveToDate is null while no successor has been published. */
public record EffectivePayrollRuleVersion(PayrollRuleVersionId id, PayrollRuleKind ruleKind, JurisdictionKey jurisdictionKey,
        LocalDate effectiveFromDate, @Nullable LocalDate effectiveToDate) {

    public EffectivePayrollRuleVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ruleKind, "ruleKind");
        Objects.requireNonNull(jurisdictionKey, "jurisdictionKey");
        Objects.requireNonNull(effectiveFromDate, "effectiveFromDate");
    }
}
