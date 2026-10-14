package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitView;

/** One org unit. Ids are strings: ids are signed 64-bit numbers that JavaScript cannot hold exactly. */
record OrgUnitResponse(String orgUnitId, String code, String name, String parentOrgUnitId, Integer sortOrder,
                       String status, String headEmployeeId, String regionalReceivablesManagerId, String regionCategory,
                       boolean deliveryUnit, String shortName) {

    static OrgUnitResponse of(OrgUnitView view) {
        return new OrgUnitResponse(String.valueOf(view.orgUnitId()), view.code(), view.name(), text(view.parentOrgUnitId()),
                view.sortOrder(), view.status(), text(view.headEmployeeId()),
                text(view.regionalReceivablesManagerId()), view.regionCategory(), view.deliveryUnit(), view.shortName());
    }

    static String text(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
