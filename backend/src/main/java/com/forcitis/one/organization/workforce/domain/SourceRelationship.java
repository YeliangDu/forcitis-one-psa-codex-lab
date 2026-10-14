package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/** the source "person is head / regional receivables manager of org unit" relation. memberDeleted: the person is deleted in the source. */
public record SourceRelationship(RelationshipKind kind, OrgUnitId orgUnitId, UserId memberId, boolean memberDeleted) {

    public SourceRelationship {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(memberId, "memberId");
    }
}
