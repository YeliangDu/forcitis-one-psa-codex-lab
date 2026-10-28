package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One version of a role's definition (ADR-0034 decision 1 item 2): every change of the name, status, scope or permission set is a new one. */
@ValueObject
public record RoleVersionId(UUID value) {

    public RoleVersionId {
        value = Uuids.requireKey(value, "RoleVersionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
