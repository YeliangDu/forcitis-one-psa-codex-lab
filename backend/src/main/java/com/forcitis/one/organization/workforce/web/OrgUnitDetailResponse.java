package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitDetailView;
import java.util.List;

/** The unit-and-below scope never contains revoked units. */
record OrgUnitDetailResponse(OrgUnitResponse orgUnit, List<String> ancestorOrgUnitIds, List<String> orgUnitAndBelowIds) {
    static OrgUnitDetailResponse of(OrgUnitDetailView view) {
        return new OrgUnitDetailResponse(OrgUnitResponse.of(view.orgUnit()),
                view.ancestorOrgUnitIds().stream().map(String::valueOf).toList(),
                view.orgUnitAndBelowIds().stream().map(String::valueOf).toList());
    }
}
