package com.forcitis.one.kernel;

import java.util.UUID;

@ValueObject
public record WbsNodeId(UUID value) {

    public WbsNodeId {
        value = Uuids.requireKey(value, "WbsNodeId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
