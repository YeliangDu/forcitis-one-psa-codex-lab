package com.forcitis.one.organization.workforce.application;

import java.util.List;

public record OrgUnitDetailView(OrgUnitView orgUnit, List<Long> ancestorOrgUnitIds, List<Long> orgUnitAndBelowIds) {
}
