package com.forcitis.one.platform.integration.api;

import java.util.List;

/**
 * {@code relationships} are ordered by the source's person id, signed ascending (negative ids first): of several heads or regional
 * receivables managers of one unit the first listed is the one with the smallest source id (RULE-356), and own ids carry no order.
 */
public record SourceOrgSnapshot(List<SourceOrgUnit> units, List<SourceRelationship> relationships,
                                List<SourcePosition> positions, List<SourcePerson> staff) {

    public SourceOrgSnapshot {
        units = List.copyOf(units);
        relationships = List.copyOf(relationships);
        positions = List.copyOf(positions);
        staff = List.copyOf(staff);
    }
}
