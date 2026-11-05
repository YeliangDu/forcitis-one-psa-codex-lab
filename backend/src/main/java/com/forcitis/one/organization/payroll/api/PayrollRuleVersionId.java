package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record PayrollRuleVersionId(UUID value) {

    public PayrollRuleVersionId {
        value = Uuids.requireKey(value, "PayrollRuleVersionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
