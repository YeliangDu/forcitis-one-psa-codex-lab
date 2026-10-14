package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port of the sync run log. */
public interface SyncRunRepository {

    SyncRunId begin(SyncTrigger trigger, Instant now);

    void markSucceeded(SyncRunId syncRunId, Instant now, RunStats stats);

    /**
     * Records the hold of the shrink guard. While an earlier run is still held, that run is refreshed instead and
     * this attempt's row is dropped, so a stuck hold does not add a row every five minutes.
     *
     * @return the run that now carries the hold
     */
    SyncRunId markHeld(SyncRunId syncRunId, Instant now, RunStats stats, String reason);

    void markFailed(SyncRunId syncRunId, Instant now, String reason);

    /** The held run was released by an administrator; the real work happened in releasedByRun. */
    void markReleased(SyncRunId held, SyncRunId releasedByRun, UserId releasedBy, Instant now);

    void saveDiffs(SyncRunId syncRunId, List<SyncDiff> diffs);

    Optional<SyncRun> find(SyncRunId syncRunId);

    List<SyncRun> findRecent(int limit);

    List<SyncDiff> findDiffs(SyncRunId syncRunId);

    Optional<SyncRun> findLatest();

    Optional<Instant> findLastSucceededAt();

    int countHeld();

    /** Employed people without a WeCom user id after the latest successful run (RULE-374); empty before the first run. */
    Optional<Integer> findLatestEmployedWithoutWecomCount();
}
