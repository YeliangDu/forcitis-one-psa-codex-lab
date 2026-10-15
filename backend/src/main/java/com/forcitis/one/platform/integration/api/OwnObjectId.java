package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** The own id of the object a source reference points at; its kind says whether it is an employee, an organisation unit, a position or a legal entity. */
@ValueObject
public record OwnObjectId(UUID value) {

    public OwnObjectId {
        value = Uuids.requireKey(value, "OwnObjectId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
