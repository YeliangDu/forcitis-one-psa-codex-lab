package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.time.Instant;

public record SyncRunView(long syncRunId, String trigger, String status, Instant startedAt, Instant finishedAt,
                          Integer sourceOrgUnitCount, Integer sourceEmployeeCount, Integer incomingActiveOrgUnitCount,
                          Integer incomingEmployedCount, Integer previousActiveOrgUnitCount,
                          Integer previousEmployedCount, Integer orgUnitChangeCount, Integer employeeChangeCount,
                          Integer positionChangeCount, Integer isolatedRowCount, Integer issueCount,
                          Integer employedWithoutWecomCount, String heldReason, String failureReason, Long releasedBy,
                          Instant releasedAt, Long releasedByRunId) {
    static SyncRunView of(SyncRunRecord run) {
        RunStats s = run.stats();
        return new SyncRunView(run.syncRunId().value(), run.trigger().name(), run.status().name(), run.startedAt(),
                run.finishedAt(),
                s == null ? null : s.sourceOrgUnitCount(), s == null ? null : s.sourceEmployeeCount(),
                s == null ? null : s.incomingActiveOrgUnitCount(), s == null ? null : s.incomingEmployedCount(),
                s == null ? null : s.previousActiveOrgUnitCount(), s == null ? null : s.previousEmployedCount(),
                s == null ? null : s.orgUnitChangeCount(), s == null ? null : s.employeeChangeCount(),
                s == null ? null : s.positionChangeCount(), s == null ? null : s.isolatedRowCount(),
                s == null ? null : s.issueCount(), s == null ? null : s.employedWithoutWecomCount(),
                run.heldReason(), run.failureReason(),
                run.releasedBy() == null ? null : run.releasedBy().value(), run.releasedAt(),
                run.releasedByRunId() == null ? null : run.releasedByRunId().value());
    }
}
