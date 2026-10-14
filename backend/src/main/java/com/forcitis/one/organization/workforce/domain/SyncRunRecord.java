package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.HoldKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The stored facts of a run, for reading; changes go through SyncRun. */
public record SyncRunRecord(SyncRunId syncRunId, SyncTrigger trigger, SyncRunStatus status, Instant startedAt,
                            Instant finishedAt, RunStats stats, String heldReason, String failureReason, UserId releasedBy,
                            Instant releasedAt, SyncRunId releasedByRunId, Set<HoldKind> holdKinds, Set<HoldKind> releasedKinds,
                            List<UserId> heldSeparations) {
    public SyncRunRecord {
        Objects.requireNonNull(syncRunId, "syncRunId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        holdKinds = Set.copyOf(holdKinds);
        releasedKinds = Set.copyOf(releasedKinds);
        heldSeparations = List.copyOf(heldSeparations);
    }
}
