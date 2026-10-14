package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Objects;

/** NULL source flags count as valid; their names are kept in nullFlags for the exception report. */
public record SourceOrgUnit(OrgUnitId orgUnitId, String code, String name, OrgUnitId parentOrgUnitId,
                            Integer sortOrder, OrgUnitStatus status, List<String> nullFlags, RegionCategory regionCategory,
                            String rawCategory, boolean deliveryUnit, OrgUnitId duplicateOfUnitId) {
    public SourceOrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        nullFlags = List.copyOf(nullFlags);
    }
}
