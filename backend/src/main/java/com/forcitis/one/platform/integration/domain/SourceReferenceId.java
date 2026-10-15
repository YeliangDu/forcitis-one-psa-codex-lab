package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record SourceReferenceId(UUID value) {

    public SourceReferenceId {
        value = Uuids.requireKey(value, "SourceReferenceId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
