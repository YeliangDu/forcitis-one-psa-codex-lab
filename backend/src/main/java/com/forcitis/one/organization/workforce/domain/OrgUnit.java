package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/**
 * One org unit of the snapshot. parentOrgUnitId is null for the root; shortName is not an A8 column (refreshed daily
 * from the old system, so it is not part of the synced fields).
 */
public record OrgUnit(OrgUnitId orgUnitId, String code, String name, OrgUnitId parentOrgUnitId, String path,
                      Integer sortOrder, OrgUnitStatus status, UserId headEmployeeId,
                      UserId regionalReceivablesManagerId, RegionCategory regionCategory, boolean deliveryUnit,
                      String shortName) {

    public OrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(status, "status");
    }

    public OrgUnit withStatus(OrgUnitStatus newStatus) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, path, sortOrder, newStatus, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, shortName);
    }

    public OrgUnit withShortName(String newShortName) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, path, sortOrder, status, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, newShortName);
    }
}
