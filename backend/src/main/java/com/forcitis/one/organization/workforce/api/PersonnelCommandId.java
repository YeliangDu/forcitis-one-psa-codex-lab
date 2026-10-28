package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/**
 * One personnel command (ADR-0068 item 1.2): a UUIDv7 the workforce application makes through kernel.IdGenerator, never one a client
 * supplies; hires, transfers, separations, reinstatements and corrections share this one id space.
 */
@ValueObject
public record PersonnelCommandId(UUID value) {

    public PersonnelCommandId {
        value = Uuids.requireKey(value, "PersonnelCommandId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
