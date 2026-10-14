package com.forcitis.one.organization.workforce.domain;

import java.util.Objects;

/** One position as read from the source view (deleted and disabled ones included so they can be synced, RULE-367). */
public record SourcePosition(PositionId positionId, String code, String name, Integer sortOrder, PositionStatus status) {

    public SourcePosition {
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }
}
