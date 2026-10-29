package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** shortName is not delivered by the source (refreshed daily from the old system), so it is not a synced field. */
public record OrgUnit(OrgUnitId orgUnitId, String code, String name, @Nullable OrgUnitId parentOrgUnitId,
                      @Nullable Integer sortOrder, OrgUnitStatus status, @Nullable EmployeeId headEmployeeId,
                      @Nullable EmployeeId regionalReceivablesManagerId, @Nullable RegionCategory regionCategory, boolean deliveryUnit,
                      @Nullable String shortName) {
    public OrgUnit {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }

    public OrgUnit withStatus(OrgUnitStatus newStatus) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, sortOrder, newStatus, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, shortName);
    }

    public OrgUnit withShortName(@Nullable String newShortName) {
        return new OrgUnit(orgUnitId, code, name, parentOrgUnitId, sortOrder, status, headEmployeeId,
                regionalReceivablesManagerId, regionCategory, deliveryUnit, newShortName);
    }
}
