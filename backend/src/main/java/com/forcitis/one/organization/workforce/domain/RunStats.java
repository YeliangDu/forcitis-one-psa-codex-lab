package com.forcitis.one.organization.workforce.domain;

/**
 * Counts of one sync run. incoming* are the numerators and previous* the denominators of the shrink guard;
 * employedWithoutWecomCount is the "employed but no WeCom user id" data-quality figure (RULE-374).
 */
public record RunStats(int sourceOrgUnitCount, int sourceEmployeeCount, int incomingActiveOrgUnitCount,
                       int incomingEmployedCount, int previousActiveOrgUnitCount, int previousEmployedCount,
                       int orgUnitChangeCount, int employeeChangeCount, int positionChangeCount,
                       int isolatedRowCount, int issueCount, int employedWithoutWecomCount) {
}
