package com.forcitis.one.organization.payroll.domain;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** What the publish check needs of a rule row: its kind and the bounds of a band. Values are not read or computed here. */
public record PayrollRuleRowBound(PayrollRuleRowKind kind, @Nullable BigDecimal lowerBound, @Nullable BigDecimal upperBound) {

    public PayrollRuleRowBound {
        Objects.requireNonNull(kind, "kind");
    }
}
