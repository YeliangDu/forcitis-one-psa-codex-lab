package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Reading side of the run log: records and counts, never aggregates. */
public interface SyncRunQueries {
    Optional<SyncRunRecord> find(SyncRunId syncRunId);

    /** Newest first: runs with an id below beforeId (null for the first page), at most limit. */
    List<SyncRunRecord> findBefore(Long beforeId, int limit);

    List<SyncDiff> findDiffs(SyncRunId syncRunId);

    Optional<Instant> findLastSucceededAt();

    int countHeld();

    /** Runs waiting for a person, oldest first. */
    List<SyncRunRecord> findHeld();

    Optional<Integer> findLatestEmployedWithoutWecomCount();
}
