package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.SyncRunView;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

record SyncRunResponse(String syncRunId, String trigger, String status, Instant startedAt, @Nullable Instant finishedAt,
                       @Nullable Integer sourceOrgUnitCount, @Nullable Integer sourceEmployeeCount, @Nullable Integer incomingActiveOrgUnitCount,
                       @Nullable Integer incomingEmployedCount, @Nullable Integer previousActiveOrgUnitCount, @Nullable Integer previousEmployedCount,
                       @Nullable Integer orgUnitChangeCount, @Nullable Integer employeeChangeCount, @Nullable Integer positionChangeCount,
                       @Nullable Integer isolatedRowCount, @Nullable Integer issueCount, @Nullable Integer employedWithoutWecomCount,
                       @Nullable String heldReason, @Nullable String failureReason, @Nullable String releasedBy, @Nullable Instant releasedAt,
                       @Nullable String releasedByRunId, List<String> holdKinds, List<String> releasedKinds, List<String> heldSeparations) {
    static SyncRunResponse of(SyncRunView view) {
        return new SyncRunResponse(view.syncRunId().value().toString(), view.trigger(), view.status(), view.startedAt(),
                view.finishedAt(), view.sourceOrgUnitCount(), view.sourceEmployeeCount(), view.incomingActiveOrgUnitCount(),
                view.incomingEmployedCount(), view.previousActiveOrgUnitCount(), view.previousEmployedCount(),
                view.orgUnitChangeCount(), view.employeeChangeCount(), view.positionChangeCount(), view.isolatedRowCount(),
                view.issueCount(), view.employedWithoutWecomCount(), view.heldReason(), view.failureReason(),
                OrgUnitResponse.text(view.releasedBy()), view.releasedAt(), view.releasedByRunId() == null ? null : view.releasedByRunId().value().toString(),
                view.holdKinds(), view.releasedKinds(), view.heldSeparations().stream().map(String::valueOf).toList());
    }
}
