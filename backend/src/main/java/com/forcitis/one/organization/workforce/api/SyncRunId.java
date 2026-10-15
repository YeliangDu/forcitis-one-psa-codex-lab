package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record SyncRunId(UUID value) {

    public SyncRunId {
        value = Uuids.requireKey(value, "SyncRunId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
