package com.forcitis.one.organization.workforce.domain;

/** incoming* are the numerators and previous* the denominators of the shrink guard. */
public record RunStats(int sourceOrgUnitCount, int sourceEmployeeCount, int incomingActiveOrgUnitCount,
                       int incomingEmployedCount, int previousActiveOrgUnitCount, int previousEmployedCount,
                       int orgUnitChangeCount, int employeeChangeCount, int positionChangeCount,
                       int isolatedRowCount, int issueCount, int employedWithoutWecomCount) {
}
