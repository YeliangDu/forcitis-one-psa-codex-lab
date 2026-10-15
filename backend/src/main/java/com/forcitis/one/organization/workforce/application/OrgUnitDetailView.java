package com.forcitis.one.organization.workforce.application;

import java.util.List;
import java.util.UUID;

public record OrgUnitDetailView(OrgUnitView orgUnit, List<UUID> ancestorOrgUnitIds, List<UUID> orgUnitAndBelowIds) {
}
