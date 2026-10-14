package com.forcitis.one.organization.workforce.domain;

import java.util.List;

/** Everything one full read of the A8 views returns. */
public record SourceSnapshot(List<SourceOrgUnit> orgUnits, List<SourceRelationship> relationships,
                             List<SourcePosition> positions, List<SourceMember> members) {

    public SourceSnapshot {
        orgUnits = List.copyOf(orgUnits);
        relationships = List.copyOf(relationships);
        positions = List.copyOf(positions);
        members = List.copyOf(members);
    }
}
