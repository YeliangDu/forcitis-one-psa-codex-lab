package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Re-runs the sync with the guard bypassed; it reads the source afresh, so the administrator releases what the source says now. */
@Service
public class ReleaseSyncRun {
    private final SyncRunRepository runs;
    private final SyncRunQueries queries;
    private final SyncOrgSnapshot sync;
    private final RecordSyncRunRelease record;

    ReleaseSyncRun(SyncRunRepository runs, SyncRunQueries queries, SyncOrgSnapshot sync, RecordSyncRunRelease record) {
        this.runs = runs;
        this.queries = queries;
        this.sync = sync;
        this.record = record;
    }

    public SyncRunView execute(long syncRunId, long actorUserId, Reason reason) {
        Objects.requireNonNull(reason, "reason");
        SyncRun run = syncRunId <= 0 ? null : runs.find(new SyncRunId(syncRunId)).orElse(null);
        if (run == null) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND, ErrorKind.NOT_FOUND, "no sync run " + syncRunId);
        }
        if (run.status() != SyncRunStatus.HELD) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL, ErrorKind.CONFLICT,
                    "the run is " + run.status() + ", only a HELD run can be released");
        }
        SyncResult result = sync.execute(SyncTrigger.MANUAL, true);
        if (result.status() != SyncRunStatus.SUCCEEDED) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED, ErrorKind.CONFLICT,
                    result.note() == null ? result.status().name() : result.note());
        }
        record.execute(run.syncRunId(), result.syncRunId(), new UserId(actorUserId), reason);
        return queries.find(result.syncRunId()).map(SyncRunView::of).orElseThrow();
    }
}
