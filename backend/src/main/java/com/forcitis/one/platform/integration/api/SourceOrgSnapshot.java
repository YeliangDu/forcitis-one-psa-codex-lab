package com.forcitis.one.platform.integration.api;

import java.util.List;

public record SourceOrgSnapshot(List<SourceOrgUnit> units, List<SourceRelationship> relationships,
                                List<SourcePosition> positions, List<SourcePerson> staff) {

    public SourceOrgSnapshot {
        units = List.copyOf(units);
        relationships = List.copyOf(relationships);
        positions = List.copyOf(positions);
        staff = List.copyOf(staff);
    }
}
