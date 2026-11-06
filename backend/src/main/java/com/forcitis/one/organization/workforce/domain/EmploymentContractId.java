package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** A labour contract of one employment relationship: a UUIDv7 the workforce application makes through kernel.IdGenerator. */
@ValueObject
public record EmploymentContractId(UUID value) {

    public EmploymentContractId {
        value = Uuids.requireKey(value, "EmploymentContractId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
