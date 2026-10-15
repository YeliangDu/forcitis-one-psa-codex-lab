package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitDetailView;
import java.util.List;
import java.util.UUID;

/** The unit-and-below scope never contains revoked units. */
record OrgUnitDetailResponse(OrgUnitResponse orgUnit, List<UUID> ancestorOrgUnitIds, List<UUID> orgUnitAndBelowIds) {
    static OrgUnitDetailResponse of(OrgUnitDetailView view) {
        return new OrgUnitDetailResponse(OrgUnitResponse.of(view.orgUnit()), view.ancestorOrgUnitIds(), view.orgUnitAndBelowIds());
    }
}
