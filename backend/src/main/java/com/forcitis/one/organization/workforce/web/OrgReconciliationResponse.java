package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.OrgReconciliationView;
import java.util.List;

record OrgReconciliationResponse(boolean zeroDifference, int sourceEmployedCount, int snapshotEmployedCount, int sourceOrgUnitCount,
                                 int snapshotOrgUnitCount, List<String> missingInSnapshot, List<String> unexpectedInSnapshot,
                                 List<String> parentMismatches, List<String> reportingLineMismatches, List<String> responsibilityMismatches,
                                 List<String> wecomUserIdMismatches) {
    static OrgReconciliationResponse of(OrgReconciliationView view) {
        return new OrgReconciliationResponse(view.zeroDifference(), view.sourceEmployedCount(), view.snapshotEmployedCount(),
                view.sourceOrgUnitCount(), view.snapshotOrgUnitCount(), view.missingInSnapshot(), view.unexpectedInSnapshot(),
                view.parentMismatches(), view.reportingLineMismatches(), view.responsibilityMismatches(), view.wecomUserIdMismatches());
    }
}
