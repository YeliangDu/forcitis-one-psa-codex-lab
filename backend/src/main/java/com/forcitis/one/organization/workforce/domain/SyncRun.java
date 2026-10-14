package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.Objects;

/** One sync run as recorded. stats is null until the run has read A8 and computed the differences. */
public record SyncRun(SyncRunId syncRunId, SyncTrigger trigger, SyncRunStatus status, Instant startedAt,
                      Instant finishedAt, RunStats stats, String heldReason, String failureReason, UserId releasedBy,
                      Instant releasedAt, SyncRunId releasedByRunId) {

    public SyncRun {
        Objects.requireNonNull(syncRunId, "syncRunId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
