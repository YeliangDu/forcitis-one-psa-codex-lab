package com.forcitis.one.organization.workforce.domain;

/** Counts of one reconciliation of the source against the snapshot. */
public record RunStats(int sourceOrgUnitCount, int sourceEmployeeCount, int incomingActiveOrgUnitCount,
                       int incomingEmployedCount, int previousActiveOrgUnitCount, int previousEmployedCount,
                       int orgUnitChangeCount, int employeeChangeCount, int positionChangeCount,
                       int isolatedRowCount, int issueCount, int employedWithoutWecomCount) {
}
