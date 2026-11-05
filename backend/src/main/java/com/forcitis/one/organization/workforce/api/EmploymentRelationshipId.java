package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One employment relationship (ADR-0086): a UUIDv7 the workforce application makes through kernel.IdGenerator when a HIRE enters, never one a client supplies. */
@ValueObject
public record EmploymentRelationshipId(UUID value) {

    public EmploymentRelationshipId {
        value = Uuids.requireKey(value, "EmploymentRelationshipId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
