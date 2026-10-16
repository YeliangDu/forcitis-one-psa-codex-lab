package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record ApprovalRequestId(UUID value) {

    public ApprovalRequestId {
        value = Uuids.requireKey(value, "ApprovalRequestId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
