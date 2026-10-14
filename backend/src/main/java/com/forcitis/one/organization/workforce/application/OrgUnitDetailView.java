package com.forcitis.one.organization.workforce.application;

import java.util.List;

/** One org unit with its ancestors (nearest first) and the unit-and-below scope without revoked units (RULE-360). */
public record OrgUnitDetailView(OrgUnitView orgUnit, List<Long> ancestorOrgUnitIds, List<Long> orgUnitAndBelowIds) {
}
