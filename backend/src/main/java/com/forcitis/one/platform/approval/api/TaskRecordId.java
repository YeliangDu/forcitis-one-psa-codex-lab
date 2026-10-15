package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record TaskRecordId(UUID value) {

    public TaskRecordId {
        value = Uuids.requireKey(value, "TaskRecordId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
