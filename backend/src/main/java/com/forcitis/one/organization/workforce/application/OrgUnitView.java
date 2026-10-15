package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.util.UUID;

public record OrgUnitView(UUID orgUnitId, String code, String name, UUID parentOrgUnitId, Integer sortOrder,
                          String status, UUID headEmployeeId, UUID regionalReceivablesManagerId, String regionCategory,
                          boolean deliveryUnit, String shortName) {
    static OrgUnitView of(OrgUnit unit) {
        return new OrgUnitView(unit.orgUnitId().value(), unit.code(), unit.name(),
                unit.parentOrgUnitId() == null ? null : unit.parentOrgUnitId().value(), unit.sortOrder(),
                unit.status().name(), unit.headEmployeeId() == null ? null : unit.headEmployeeId().value(),
                unit.regionalReceivablesManagerId() == null ? null : unit.regionalReceivablesManagerId().value(),
                unit.regionCategory() == null ? null : unit.regionCategory().name(), unit.deliveryUnit(), unit.shortName());
    }
}
