package com.forcitis.one.organization.recruitment.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record RequisitionId(UUID value) {

    public RequisitionId {
        value = Uuids.requireKey(value, "RequisitionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
