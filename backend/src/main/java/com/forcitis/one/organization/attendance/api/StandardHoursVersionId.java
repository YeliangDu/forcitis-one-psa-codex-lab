package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record StandardHoursVersionId(UUID value) {

    public StandardHoursVersionId {
        value = Uuids.requireKey(value, "StandardHoursVersionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
