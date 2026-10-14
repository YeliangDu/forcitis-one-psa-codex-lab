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
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lifts the holds the actor is entitled to; once none is left it re-runs the sync, which reads the source afresh, so the person
 * releases what the source says now and a new administrator among the leavers puts the run back on hold.
 */
@Service
public class ReleaseSyncRun {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseSyncRun.class);

    private final SyncRunRepository runs;
    private final SyncRunQueries queries;
    private final SyncOrgSnapshot sync;
    private final ReleaseSyncRunHolds lift;
    private final RecordSyncRunRelease record;

    ReleaseSyncRun(SyncRunRepository runs, SyncRunQueries queries, SyncOrgSnapshot sync, ReleaseSyncRunHolds lift, RecordSyncRunRelease record) {
        this.runs = runs;
        this.queries = queries;
        this.sync = sync;
        this.lift = lift;
        this.record = record;
    }

    public ReleaseResult execute(long syncRunId, long actorUserId, String reason, boolean mayLiftShrinkGuard, boolean mayLiftSeparation) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED, ErrorKind.INVALID,
                    "a reason is required to release a held sync run");
        }
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
        UserId actor = new UserId(actorUserId);
        ReleaseSyncRunHolds.Lifted lifted = lift.execute(held, actor, mayLiftShrinkGuard, mayLiftSeparation).orElseThrow();
        if (!lifted.run().allHoldsReleased()) {
            return new ReleaseResult.PartlyReleased(queries.find(held).map(SyncRunView::of).orElseThrow());
        }
        SyncResult result = sync.release(SyncTrigger.MANUAL, actor);
        if (result.status() != SyncRunStatus.SUCCEEDED) {
            return new ReleaseResult.NotApplied(result.note() == null ? result.status().name() : result.note());
        }
        String detail = "reason: " + reason.strip() + "; session mode: " + (lifted.review().sessionMode().isEmpty() ? "none" : lifted.review().sessionMode()) + "; held: "
                + ids(lifted.run()) + "; applied: " + result.separated().stream().map(id -> String.valueOf(id.value())).collect(Collectors.joining(","))
                + "; remaining super_admin: " + lifted.review().remainingAdministrators();
        record.execute(held, result.syncRunId(), actor, new Reason(reason.strip()), detail);
        LOG.error("ALERT organization.workforce sync run {} released by user {}: {}", held.value(), actorUserId, detail);
        return new ReleaseResult.Released(queries.find(result.syncRunId()).map(SyncRunView::of).orElseThrow());
    }

    private static String ids(SyncRun run) {
        return run.heldSeparations().stream().map(id -> String.valueOf(id.value())).collect(Collectors.joining(","));
    }
}
