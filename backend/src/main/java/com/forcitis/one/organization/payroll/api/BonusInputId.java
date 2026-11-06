package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One approved bonus input: a UUIDv7 payroll makes when the approval completes; it is not the approval's document id. */
@ValueObject
public record BonusInputId(UUID value) {

    public BonusInputId {
        value = Uuids.requireKey(value, "BonusInputId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
