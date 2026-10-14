package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgUnitDetailView;
import java.util.List;

/** One org unit with its ancestors (nearest first) and the unit-and-below scope, which never contains revoked units. */
record OrgUnitDetailResponse(OrgUnitResponse orgUnit, List<String> ancestorOrgUnitIds, List<String> orgUnitAndBelowIds) {

    static OrgUnitDetailResponse of(OrgUnitDetailView view) {
        return new OrgUnitDetailResponse(OrgUnitResponse.of(view.orgUnit()),
                view.ancestorOrgUnitIds().stream().map(String::valueOf).toList(),
                view.orgUnitAndBelowIds().stream().map(String::valueOf).toList());
    }
}
