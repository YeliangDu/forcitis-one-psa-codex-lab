package com.forcitis.one.platform.integration.api;

import java.util.List;
import java.util.Objects;

public record SourceOrgUnit(SourceUnitId unitId, String code, String name, SourceUnitId parentUnitId, Integer sortOrder,
                            SourceRecordState state, List<String> emptyFlags, SourceRegionKind regionKind,
                            String rawRegion, boolean deliveryUnit, SourceUnitId duplicateOfUnitId) {

    public SourceOrgUnit {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
        emptyFlags = List.copyOf(emptyFlags);
    }
}
