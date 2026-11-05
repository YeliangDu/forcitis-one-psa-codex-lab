package com.forcitis.one.accounting.period.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record FiscalPeriodId(UUID value) {

    public FiscalPeriodId {
        value = Uuids.requireKey(value, "FiscalPeriodId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
