package com.forcitis.one.organization.workforce.domain;

import java.util.List;

/**
 * Comparison of the stored snapshot with what A8 says now (exit criterion "组织零差异", the base is A8):
 * people count (employed), org unit count, and the "上级关系" - the org unit parent and the person's direct leader.
 * Lists hold subject keys such as "employee:2001" or "org-unit:15".
 */
public record OrgReconciliationReport(int sourceEmployedCount, int snapshotEmployedCount, int sourceOrgUnitCount,
                                      int snapshotOrgUnitCount, int sourceActiveOrgUnitCount,
                                      int snapshotActiveOrgUnitCount, List<String> missingInSnapshot,
                                      List<String> unexpectedInSnapshot, List<String> parentMismatches,
                                      List<String> reportingLineMismatches) {

    public OrgReconciliationReport {
        missingInSnapshot = List.copyOf(missingInSnapshot);
        unexpectedInSnapshot = List.copyOf(unexpectedInSnapshot);
        parentMismatches = List.copyOf(parentMismatches);
        reportingLineMismatches = List.copyOf(reportingLineMismatches);
    }

    /** True only when every figure matches and no listed difference exists. */
    public boolean isZeroDifference() {
        return sourceEmployedCount == snapshotEmployedCount
                && sourceOrgUnitCount == snapshotOrgUnitCount
                && sourceActiveOrgUnitCount == snapshotActiveOrgUnitCount
                && missingInSnapshot.isEmpty() && unexpectedInSnapshot.isEmpty()
                && parentMismatches.isEmpty() && reportingLineMismatches.isEmpty();
    }
}
