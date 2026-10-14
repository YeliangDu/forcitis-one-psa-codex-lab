package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.SyncRunView;
import java.time.Instant;

record SyncRunResponse(String syncRunId, String trigger, String status, Instant startedAt, Instant finishedAt,
                       Integer sourceOrgUnitCount, Integer sourceEmployeeCount, Integer incomingActiveOrgUnitCount,
                       Integer incomingEmployedCount, Integer previousActiveOrgUnitCount, Integer previousEmployedCount,
                       Integer orgUnitChangeCount, Integer employeeChangeCount, Integer positionChangeCount,
                       Integer isolatedRowCount, Integer issueCount, Integer employedWithoutWecomCount,
                       String heldReason, String failureReason, String releasedBy, Instant releasedAt,
                       String releasedByRunId) {
    static SyncRunResponse of(SyncRunView view) {
        return new SyncRunResponse(String.valueOf(view.syncRunId()), view.trigger(), view.status(), view.startedAt(),
                view.finishedAt(), view.sourceOrgUnitCount(), view.sourceEmployeeCount(), view.incomingActiveOrgUnitCount(),
                view.incomingEmployedCount(), view.previousActiveOrgUnitCount(), view.previousEmployedCount(),
                view.orgUnitChangeCount(), view.employeeChangeCount(), view.positionChangeCount(), view.isolatedRowCount(),
                view.issueCount(), view.employedWithoutWecomCount(), view.heldReason(), view.failureReason(),
                OrgUnitResponse.text(view.releasedBy()), view.releasedAt(), OrgUnitResponse.text(view.releasedByRunId()));
    }
}
