package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;

/** Typed id of an org unit; same value as A8 ORG_UNIT.ID (signed bigint). */
@ValueObject
public record OrgUnitId(long value) {

    public OrgUnitId {
        if (value == 0) {
            throw new IllegalArgumentException("OrgUnitId must not be 0");
        }
    }
}
