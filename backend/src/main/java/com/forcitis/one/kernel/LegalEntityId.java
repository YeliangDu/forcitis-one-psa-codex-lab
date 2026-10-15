package com.forcitis.one.kernel;

import java.util.UUID;

@ValueObject
public record LegalEntityId(UUID value) {

    public LegalEntityId {
        value = Uuids.requireKey(value, "LegalEntityId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
