package com.forcitis.one.platform.integration.api;

import java.util.Objects;

public record SourceRelationship(SourceRelationshipKind kind, OwnObjectId unitId, OwnObjectId personId, boolean personDeleted) {

    public SourceRelationship {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(personId, "personId");
    }
}
