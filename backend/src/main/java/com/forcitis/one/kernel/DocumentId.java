package com.forcitis.one.kernel;

import java.util.UUID;

@ValueObject
public record DocumentId(UUID value) {

    public DocumentId {
        value = Uuids.requireKey(value, "DocumentId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
