package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import java.util.Objects;

public record Position(PositionId positionId, String code, String name, Integer sortOrder, PositionStatus status) {
    public Position {
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }

    public Position withStatus(PositionStatus newStatus) {
        return new Position(positionId, code, name, sortOrder, newStatus);
    }
}
