package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Writing side. A save applies only to the stored status and version the run was loaded with; otherwise it is refused. */
public interface SyncRunRepository {
    SyncRun insertRunning(SyncTrigger trigger, Instant now);

    Optional<SyncRun> find(SyncRunId syncRunId);

    /** RUNNING runs started before the cutoff: the process that owned them is gone. */
    List<SyncRun> findAbandoned(Instant cutoff);

    /** Held runs other than the given one, oldest first. */
    List<SyncRun> findHeldExcept(SyncRunId syncRunId);

    void save(SyncRun run);

    /** Removes a run that never got past RUNNING (its figures were merged into an earlier held run). */
    void delete(SyncRun run);

    void saveDiffs(SyncRunId syncRunId, List<SyncDiff> diffs);
}
