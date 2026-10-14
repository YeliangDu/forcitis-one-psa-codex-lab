package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;

@ValueObject
public record OrgUnitId(long value) {
    public OrgUnitId {
        if (value == 0) {
            throw new IllegalArgumentException("OrgUnitId must not be 0");
        }
    }
}
