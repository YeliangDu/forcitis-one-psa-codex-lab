package com.forcitis.one.kernel;

import java.util.UUID;

@ValueObject
public record ProjectId(UUID value) {

    public ProjectId {
        value = Uuids.requireKey(value, "ProjectId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
