package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.CurrentUser;
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
    static final int MAX_REASON_LENGTH = 200;
    static final String SYNC_RUN_EXECUTE = "organization.workforce:sync-run:execute";
    static final String SEPARATION_HOLD_APPROVE = "organization.workforce:separation-hold:approve";
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseSyncRun.class);

    private final SyncRunRepository runs;
    private final SyncRunQueries queries;
    private final SyncOrgSnapshot sync;
    private final ReleaseSyncRunHolds lift;
    private final RecordSyncRunRelease record;
    private final WithdrawSyncRunReleases withdraw;
    private final CurrentUser currentUser;
    private final CallerPermissions permissions;

    ReleaseSyncRun(SyncRunRepository runs, SyncRunQueries queries, SyncOrgSnapshot sync, ReleaseSyncRunHolds lift, RecordSyncRunRelease record,
            WithdrawSyncRunReleases withdraw, CurrentUser currentUser, CallerPermissions permissions) {
        this.currentUser = currentUser;
        this.permissions = permissions;
        this.withdraw = withdraw;
        this.runs = runs;
        this.queries = queries;
        this.sync = sync;
        this.lift = lift;
        this.record = record;
    }

    /** A shrink-guard hold is lifted by the holder of sync-run:execute, a separation hold by the holder of separation-hold:approve. */
    public ReleaseResult execute(SyncRunId held, String reason) {
        UserId actor = currentUser.userId();
        boolean mayLiftShrinkGuard = permissions.holds(SYNC_RUN_EXECUTE);
        boolean mayLiftSeparation = permissions.holds(SEPARATION_HOLD_APPROVE);
        if (reason == null || reason.isBlank()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED, ErrorKind.INVALID,
                    "a reason is required to release a held sync run");
        }
        if (reason.strip().length() > MAX_REASON_LENGTH) {
            LOG.error("ALERT organization.workforce the reason to release sync run {} by user {} exceeds {} characters: refused", held.value(), actor.value(),
                    MAX_REASON_LENGTH);
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED, ErrorKind.INVALID,
                    "a reason of 1.." + MAX_REASON_LENGTH + " characters is required to release a held sync run");
        }
        Optional<SyncRun> run = runs.find(held);
        if (run.isEmpty()) {
            return new ReleaseResult.NotFound();
        }
        if (run.get().status() != SyncRunStatus.HELD) {
            return new ReleaseResult.NotHeld(run.get().status().name());
        }
        String cleanReason = reason.strip().replaceAll("\\p{Cntrl}", " ");
        ReleaseSyncRunHolds.Lifted lifted = lift.execute(held, actor, mayLiftShrinkGuard, mayLiftSeparation).orElseThrow();
        if (!lifted.run().allHoldsReleased()) {
            return new ReleaseResult.PartlyReleased(queries.find(held).map(SyncRunView::of).orElseThrow());
        }
        SyncResult result = sync.release(SyncTrigger.MANUAL, actor);
        if (result.status() != SyncRunStatus.SUCCEEDED) {
            withdraw.execute(held);
            return new ReleaseResult.NotApplied(result.note() == null ? result.status().name() : result.note());
        }
        String detail = "reason: " + cleanReason + "; session mode: " + (lifted.review().sessionMode().isEmpty() ? "none" : lifted.review().sessionMode()) + "; held: "
                + ids(lifted.run()) + "; applied: " + result.separated().stream().map(id -> String.valueOf(id.value())).collect(Collectors.joining(","))
                + "; remaining super_admin: " + lifted.review().remainingAdministrators();
        record.execute(held, result.syncRunId(), actor, new Reason(cleanReason), detail);
        LOG.error("ALERT organization.workforce sync run {} released by user {}: {}", held.value(), actor.value(), detail);
        return new ReleaseResult.Released(queries.find(result.syncRunId()).map(SyncRunView::of).orElseThrow());
    }

    private static String ids(SyncRun run) {
        return run.heldSeparations().stream().map(id -> String.valueOf(id.value())).collect(Collectors.joining(","));
    }
}
