package com.forcitis.one.organization.workforce.domain;

import java.util.Objects;

/** Deleted and disabled positions are included so they can be synced. */
public record SourcePosition(PositionId positionId, String code, String name, Integer sortOrder, PositionStatus status) {
    public SourcePosition {
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }
}
