package com.forcitis.one.organization.workforce.domain;

import java.util.List;

public record OrgReconciliationReport(int sourceEmployedCount, int snapshotEmployedCount, int sourceOrgUnitCount,
                                      int snapshotOrgUnitCount, int sourceActiveOrgUnitCount,
                                      int snapshotActiveOrgUnitCount, List<String> missingInSnapshot,
                                      List<String> unexpectedInSnapshot, List<String> parentMismatches,
                                      List<String> reportingLineMismatches, List<String> responsibilityMismatches,
                                      List<String> wecomUserIdMismatches) {
    public OrgReconciliationReport {
        missingInSnapshot = List.copyOf(missingInSnapshot);
        unexpectedInSnapshot = List.copyOf(unexpectedInSnapshot);
        parentMismatches = List.copyOf(parentMismatches);
        reportingLineMismatches = List.copyOf(reportingLineMismatches);
        responsibilityMismatches = List.copyOf(responsibilityMismatches);
        wecomUserIdMismatches = List.copyOf(wecomUserIdMismatches);
    }

    public int differenceCount() {
        return missingInSnapshot.size() + unexpectedInSnapshot.size() + parentMismatches.size() + reportingLineMismatches.size()
                + responsibilityMismatches.size() + wecomUserIdMismatches.size()
                + Math.abs(sourceEmployedCount - snapshotEmployedCount);
    }

    public boolean isZeroDifference() {
        return sourceEmployedCount == snapshotEmployedCount
                && sourceOrgUnitCount == snapshotOrgUnitCount
                && sourceActiveOrgUnitCount == snapshotActiveOrgUnitCount
                && missingInSnapshot.isEmpty() && unexpectedInSnapshot.isEmpty()
                && parentMismatches.isEmpty() && reportingLineMismatches.isEmpty()
                && responsibilityMismatches.isEmpty() && wecomUserIdMismatches.isEmpty();
    }
}
