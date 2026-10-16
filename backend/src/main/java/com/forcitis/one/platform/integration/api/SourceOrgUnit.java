package com.forcitis.one.platform.integration.api;

import java.util.List;
import java.util.Objects;

public record SourceOrgUnit(OwnObjectId unitId, String code, String name, OwnObjectId parentUnitId, Integer sortOrder,
                            SourceRecordState state, List<String> emptyFlags, SourceRegionKind regionKind,
                            String rawRegion, boolean deliveryUnit, OwnObjectId duplicateOfUnitId) {

    public SourceOrgUnit {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
        emptyFlags = List.copyOf(emptyFlags);
    }
}
