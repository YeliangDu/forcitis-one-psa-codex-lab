package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One org-structure command (ADR-0068 item 4): a UUIDv7 the workforce application makes through kernel.IdGenerator, never one a client supplies. */
@ValueObject
public record OrgStructureCommandId(UUID value) {

    public OrgStructureCommandId {
        value = Uuids.requireKey(value, "OrgStructureCommandId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
