package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Reading side of the run log: records and counts, never aggregates. */
public interface SyncRunQueries {
    Optional<SyncRunRecord> find(SyncRunId syncRunId);

    /** Where a page ended: the next one starts below this start time, and below this id among runs that started at the same time. */
    record Position(Instant startedAt, SyncRunId syncRunId) {
    }

    /** Newest first by start time: runs below before (null for the first page), at most limit. */
    List<SyncRunRecord> findBefore(Position before, int limit);

    List<SyncDiff> findDiffs(SyncRunId syncRunId);

    Optional<Instant> findLastSucceededAt();

    int countHeld();

    /** The held-separation rows of runs that are still HELD: separations the old sync kept back and nobody has released or dropped. */
    int countHeldRowsOfHeldRuns();

    /** Runs waiting for a person, oldest first. */
    List<SyncRunRecord> findHeld();

    Optional<Integer> findLatestEmployedWithoutWecomCount();
}
