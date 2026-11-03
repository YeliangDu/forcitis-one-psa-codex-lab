package com.forcitis.one.organization.workforce.api;

import java.time.LocalDate;

/** Rehearses a merge of {@code targetOrgUnitId} into {@code mergeIntoOrgUnitId} on a day: who would move; nothing is written. */
public record PreviewOrgUnitMerge(OrgUnitId targetOrgUnitId, OrgUnitId mergeIntoOrgUnitId, LocalDate effectiveDate) {
}
