package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SyncRunRepository {
    SyncRunId begin(SyncTrigger trigger, Instant now);

    void markSucceeded(SyncRunId syncRunId, Instant now, RunStats stats);

    /** While an earlier run is still held it is refreshed instead, so a stuck hold adds no row every five minutes. */
    SyncRunId markHeld(SyncRunId syncRunId, Instant now, RunStats stats, String reason);

    void markFailed(SyncRunId syncRunId, Instant now, String reason);

    void markReleased(SyncRunId held, SyncRunId releasedByRun, UserId releasedBy, Instant now);

    void saveDiffs(SyncRunId syncRunId, List<SyncDiff> diffs);

    Optional<SyncRun> find(SyncRunId syncRunId);

    List<SyncRun> findRecent(int limit);

    List<SyncDiff> findDiffs(SyncRunId syncRunId);

    Optional<SyncRun> findLatest();

    Optional<Instant> findLastSucceededAt();

    int countHeld();

    Optional<Integer> findLatestEmployedWithoutWecomCount();
}
