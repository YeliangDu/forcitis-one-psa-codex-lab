package com.forcitis.one.platform.integration.api;

import java.util.Objects;

public record SourcePosition(OwnObjectId positionId, String code, String name, Integer sortOrder, SourceRecordState state) {

    public SourcePosition {
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
    }
}
