package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Objects;

/**
 * One org unit as the organisation source delivers it, already interpreted: status from the source's flags
 * (NULL counts as valid, the names of NULL flags are kept in nullFlags for the exception report), category and
 * delivery marker. parentOrgUnitId is null for the root. rawCategory is the source's wording when it is not recognised.
 * duplicateOfUnitId is set when another unit occupies the same place in the source tree.
 */
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
