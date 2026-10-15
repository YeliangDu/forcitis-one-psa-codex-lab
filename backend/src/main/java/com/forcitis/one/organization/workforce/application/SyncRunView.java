package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import com.forcitis.one.kernel.EmployeeId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncRunView(SyncRunId syncRunId, String trigger, String status, Instant startedAt, Instant finishedAt,
                          Integer sourceOrgUnitCount, Integer sourceEmployeeCount, Integer incomingActiveOrgUnitCount,
                          Integer incomingEmployedCount, Integer previousActiveOrgUnitCount,
                          Integer previousEmployedCount, Integer orgUnitChangeCount, Integer employeeChangeCount,
                          Integer positionChangeCount, Integer isolatedRowCount, Integer issueCount,
                          Integer employedWithoutWecomCount, String heldReason, String failureReason, UUID releasedBy,
                          Instant releasedAt, SyncRunId releasedByRunId, List<String> holdKinds, List<String> releasedKinds,
                          List<UUID> heldSeparations) {
    static SyncRunView of(SyncRunRecord run) {
        RunStats s = run.stats();
        return new SyncRunView(run.syncRunId(), run.trigger().name(), run.status().name(), run.startedAt(),
                run.finishedAt(),
                s == null ? null : s.sourceOrgUnitCount(), s == null ? null : s.sourceEmployeeCount(),
                s == null ? null : s.incomingActiveOrgUnitCount(), s == null ? null : s.incomingEmployedCount(),
                s == null ? null : s.previousActiveOrgUnitCount(), s == null ? null : s.previousEmployedCount(),
                s == null ? null : s.orgUnitChangeCount(), s == null ? null : s.employeeChangeCount(),
                s == null ? null : s.positionChangeCount(), s == null ? null : s.isolatedRowCount(),
                s == null ? null : s.issueCount(), s == null ? null : s.employedWithoutWecomCount(),
                run.heldReason(), run.failureReason(),
                run.releasedBy() == null ? null : run.releasedBy().value(), run.releasedAt(),
                run.releasedByRunId(),
                run.holdKinds().stream().map(Enum::name).sorted().toList(), run.releasedKinds().stream().map(Enum::name).sorted().toList(),
                run.heldSeparations().stream().map(EmployeeId::value).toList());
    }
}
