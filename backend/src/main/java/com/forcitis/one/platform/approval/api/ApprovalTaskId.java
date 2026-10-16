package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record ApprovalTaskId(UUID value) {

    public ApprovalTaskId {
        value = Uuids.requireKey(value, "ApprovalTaskId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
