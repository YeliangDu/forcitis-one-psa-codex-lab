package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Objects;

/**
 * One org unit as read from the A8 view and already interpreted by the shared mapper: status from the A8 flags
 * (NULL counts as valid, the names of NULL flags are kept in nullFlags for the exception report), category and
 * delivery marker resolved with the configured A8 ids. rawCategory is the A8 display value when it is not recognised.
 */
public record SourceOrgUnit(OrgUnitId orgUnitId, String code, String name, String path, Integer sortOrder,
                            OrgUnitStatus status, List<String> nullFlags, RegionCategory regionCategory,
                            String rawCategory, boolean deliveryUnit) {

    public SourceOrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(status, "status");
        nullFlags = List.copyOf(nullFlags);
    }
}
