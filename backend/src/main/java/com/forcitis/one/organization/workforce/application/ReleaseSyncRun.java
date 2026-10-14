package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Re-runs the sync with the guard bypassed; it reads the source afresh, so the administrator releases what the source says now. */
@Service
public class ReleaseSyncRun {
    private final SyncRunRepository runs;
    private final SyncOrgSnapshot sync;
    private final RecordSyncRunRelease record;

    ReleaseSyncRun(SyncRunRepository runs, SyncOrgSnapshot sync, RecordSyncRunRelease record) {
        this.runs = runs;
        this.sync = sync;
        this.record = record;
    }

    public ReleaseResult execute(long syncRunId, long actorUserId) {
        if (syncRunId <= 0) {
            return new ReleaseResult.NotFound();
        }
        SyncRunId held = new SyncRunId(syncRunId);
        Optional<SyncRun> run = runs.find(held);
        if (run.isEmpty()) {
            return new ReleaseResult.NotFound();
        }
        if (run.get().status() != SyncRunStatus.HELD) {
            return new ReleaseResult.NotHeld(run.get().status().name());
        }
        SyncResult result = sync.execute(SyncTrigger.MANUAL, true);
        if (result.status() != SyncRunStatus.SUCCEEDED) {
            return new ReleaseResult.NotApplied(result.note() == null ? result.status().name() : result.note());
        }
        record.execute(held, result.syncRunId(), new UserId(actorUserId));
        return new ReleaseResult.Released(runs.find(result.syncRunId()).map(SyncRunView::of).orElseThrow());
    }
}
