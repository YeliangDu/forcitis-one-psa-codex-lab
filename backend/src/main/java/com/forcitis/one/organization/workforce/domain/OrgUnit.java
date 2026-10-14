package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/** shortName is not delivered by the source (refreshed daily from the old system), so it is not a synced field. */
public record OrgUnit(OrgUnitId orgUnitId, String code, String name, OrgUnitId parentOrgUnitId,
                      Integer sortOrder, OrgUnitStatus status, UserId headEmployeeId,
                      UserId regionalReceivablesManagerId, RegionCategory regionCategory, boolean deliveryUnit,
                      String shortName) {
    public OrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }

    public OrgUnit withStatus(OrgUnitStatus newStatus) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, sortOrder, newStatus, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, shortName);
    }

    public OrgUnit withShortName(String newShortName) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, sortOrder, status, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, newShortName);
    }
}
