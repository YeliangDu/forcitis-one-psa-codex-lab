package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record OrgUnitId(UUID value) {

    public OrgUnitId {
        value = Uuids.requireKey(value, "OrgUnitId");
    }

    /** Only the lower-case canonical form. */
    public static OrgUnitId parse(String text) {
        return new OrgUnitId(Uuids.parseCanonical(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
