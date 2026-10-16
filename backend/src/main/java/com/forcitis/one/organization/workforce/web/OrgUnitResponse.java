package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record OrgUnitResponse(UUID orgUnitId, String code, String name, @Nullable UUID parentOrgUnitId, @Nullable Integer sortOrder,
                       String status, @Nullable UUID headEmployeeId, @Nullable UUID regionalReceivablesManagerId, @Nullable String regionCategory,
                       boolean deliveryUnit, @Nullable String shortName) {
    static OrgUnitResponse of(OrgUnitView view) {
        return new OrgUnitResponse(view.orgUnitId(), view.code(), view.name(), view.parentOrgUnitId(),
                view.sortOrder(), view.status(), view.headEmployeeId(),
                view.regionalReceivablesManagerId(), view.regionCategory(), view.deliveryUnit(), view.shortName());
    }
}
