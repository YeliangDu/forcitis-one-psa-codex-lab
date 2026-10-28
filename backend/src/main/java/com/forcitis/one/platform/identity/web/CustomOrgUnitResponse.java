package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import java.util.UUID;

record CustomOrgUnitResponse(UUID orgUnitId, boolean includeDescendants) {

    static CustomOrgUnitResponse of(CustomOrgUnit unit) {
        return new CustomOrgUnitResponse(unit.orgUnitId().value(), unit.includeDescendants());
    }
}
