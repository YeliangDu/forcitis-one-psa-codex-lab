package com.forcitis.one.organization.workforce.domain;

import java.util.List;

public record SourceSnapshot(List<SourceOrgUnit> orgUnits, List<SourceRelationship> relationships,
                             List<SourcePosition> positions, List<SourceMember> members) {
    public SourceSnapshot {
        orgUnits = List.copyOf(orgUnits);
        relationships = List.copyOf(relationships);
        positions = List.copyOf(positions);
        members = List.copyOf(members);
    }
}
