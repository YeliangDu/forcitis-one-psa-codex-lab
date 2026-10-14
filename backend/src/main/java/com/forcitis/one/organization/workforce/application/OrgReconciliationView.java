package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgReconciliationReport;
import java.util.List;

public record OrgReconciliationView(boolean zeroDifference, int sourceEmployedCount, int snapshotEmployedCount, int sourceOrgUnitCount,
                                    int snapshotOrgUnitCount, List<String> missingInSnapshot, List<String> unexpectedInSnapshot,
                                    List<String> parentMismatches, List<String> reportingLineMismatches,
                                    List<String> responsibilityMismatches, List<String> wecomUserIdMismatches) {
    static OrgReconciliationView of(OrgReconciliationReport report) {
        return new OrgReconciliationView(report.isZeroDifference(), report.sourceEmployedCount(), report.snapshotEmployedCount(),
                report.sourceOrgUnitCount(), report.snapshotOrgUnitCount(), report.missingInSnapshot(), report.unexpectedInSnapshot(),
                report.parentMismatches(), report.reportingLineMismatches(), report.responsibilityMismatches(),
                report.wecomUserIdMismatches());
    }
}
