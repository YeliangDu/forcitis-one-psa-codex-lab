package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitView;
import org.jspecify.annotations.Nullable;

record OrgUnitResponse(String orgUnitId, String code, String name, @Nullable String parentOrgUnitId, @Nullable Integer sortOrder,
                       String status, @Nullable String headEmployeeId, @Nullable String regionalReceivablesManagerId, @Nullable String regionCategory,
                       boolean deliveryUnit, @Nullable String shortName) {
    static OrgUnitResponse of(OrgUnitView view) {
        return new OrgUnitResponse(String.valueOf(view.orgUnitId()), view.code(), view.name(), text(view.parentOrgUnitId()),
                view.sortOrder(), view.status(), text(view.headEmployeeId()),
                text(view.regionalReceivablesManagerId()), view.regionCategory(), view.deliveryUnit(), view.shortName());
    }

    static String text(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
