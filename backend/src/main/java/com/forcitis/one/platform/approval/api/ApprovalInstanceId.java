package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record ApprovalInstanceId(UUID value) {

    public ApprovalInstanceId {
        value = Uuids.requireKey(value, "ApprovalInstanceId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
