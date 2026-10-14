package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.HoldKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.organization.workforce.events.SyncRunFailed;
import com.forcitis.one.organization.workforce.events.SyncRunHeld;
import com.forcitis.one.organization.workforce.events.SyncRunHoldReleased;
import com.forcitis.one.organization.workforce.events.SyncRunReleased;
import com.forcitis.one.organization.workforce.events.SyncRunSucceeded;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One run of the org snapshot sync. RUNNING ends as SUCCEEDED, HELD (shrink guard) or FAILED; a HELD run is refreshed by a later
 * held run and ends as RELEASED, by an administrator or by the next successful run.
 */
public final class SyncRun extends AggregateRoot {
    static final String AGGREGATE = "SyncRun";

    public static final StateMachine<SyncRunStatus> MACHINE = StateMachine.of(SyncRunStatus.class)
            .allow(SyncRunStatus.RUNNING, SyncRunStatus.SUCCEEDED, SyncRunStatus.HELD, SyncRunStatus.FAILED)
            .allow(SyncRunStatus.HELD, SyncRunStatus.HELD, SyncRunStatus.RELEASED)
            .build();

    private final SyncRunId syncRunId;
    private final SyncTrigger trigger;
    private final Instant startedAt;
    private final SyncRunStatus loadedStatus;
    private SyncRunStatus status;
    private Instant finishedAt;
    private RunStats stats;
    private String heldReason;
    private String failureReason;
    private UserId releasedBy;
    private Instant releasedAt;
    private SyncRunId releasedByRunId;
    private Set<HoldKind> holdKinds;
    private Set<HoldKind> releasedKinds;
    private List<UserId> heldSeparations;

    private SyncRun(SyncRunId syncRunId, SyncTrigger trigger, SyncRunStatus status, Instant startedAt, Instant finishedAt,
                    RunStats stats, String heldReason, String failureReason, UserId releasedBy, Instant releasedAt,
                    SyncRunId releasedByRunId, Set<HoldKind> holdKinds, Set<HoldKind> releasedKinds, List<UserId> heldSeparations, long version,
                    long eventSequence) {
        super(version, eventSequence);
        this.holdKinds = Set.copyOf(holdKinds);
        this.releasedKinds = Set.copyOf(releasedKinds);
        this.heldSeparations = List.copyOf(heldSeparations);
        this.syncRunId = syncRunId;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = finishedAt;
        this.stats = stats;
        this.heldReason = heldReason;
        this.failureReason = failureReason;
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
        this.releasedByRunId = releasedByRunId;
    }

    /** A run that is about to be inserted; the repository gives it its id with withId. */
    public static SyncRun unsaved(SyncTrigger trigger, Instant startedAt) {
        return new SyncRun(null, trigger, SyncRunStatus.RUNNING, startedAt, null, null, null, null, null, null, null, Set.of(), Set.of(), List.of(), 0, 0);
    }

    public SyncRun withId(SyncRunId id) {
        return new SyncRun(Objects.requireNonNull(id, "id"), trigger, status, startedAt, finishedAt, stats, heldReason, failureReason,
                releasedBy, releasedAt, releasedByRunId, holdKinds, releasedKinds, heldSeparations, version(), eventSequence());
    }

    /** Rebuilds a stored run; status is the stored name. */
    public static SyncRun restore(SyncRunId syncRunId, SyncTrigger trigger, String status, Instant startedAt, Instant finishedAt,
                                  RunStats stats, String heldReason, String failureReason, UserId releasedBy, Instant releasedAt,
                                  SyncRunId releasedByRunId, long version, long eventSequence) {
        return restore(syncRunId, trigger, status, startedAt, finishedAt, stats, heldReason, failureReason, releasedBy, releasedAt,
                releasedByRunId, heldReason == null ? Set.of() : Set.of(HoldKind.SHRINK_GUARD), Set.of(), List.of(), version, eventSequence);
    }

    public static SyncRun restore(SyncRunId syncRunId, SyncTrigger trigger, String status, Instant startedAt, Instant finishedAt,
                                  RunStats stats, String heldReason, String failureReason, UserId releasedBy, Instant releasedAt,
                                  SyncRunId releasedByRunId, Set<HoldKind> holdKinds, Set<HoldKind> releasedKinds,
                                  List<UserId> heldSeparations, long version, long eventSequence) {
        return new SyncRun(syncRunId, trigger, SyncRunStatus.valueOf(status), startedAt, finishedAt, stats, heldReason,
                failureReason, releasedBy, releasedAt, releasedByRunId, holdKinds, releasedKinds, heldSeparations, version, eventSequence);
    }

    public Set<HoldKind> holdKinds() {
        return holdKinds;
    }

    public Set<HoldKind> releasedKinds() {
        return releasedKinds;
    }

    public List<UserId> heldSeparations() {
        return heldSeparations;
    }

    public boolean allHoldsReleased() {
        return releasedKinds.containsAll(holdKinds);
    }

    public SyncRunId syncRunId() {
        return syncRunId;
    }

    public SyncTrigger trigger() {
        return trigger;
    }

    public SyncRunStatus status() {
        return status;
    }

    public SyncRunStatus loadedStatus() {
        return loadedStatus;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public RunStats stats() {
        return stats;
    }

    public String heldReason() {
        return heldReason;
    }

    public String failureReason() {
        return failureReason;
    }

    public UserId releasedBy() {
        return releasedBy;
    }

    public Instant releasedAt() {
        return releasedAt;
    }

    public SyncRunId releasedByRunId() {
        return releasedByRunId;
    }

    /** snapshotDate is the business day on which the source was read; the source gives no earlier date for what changed. */
    public void succeed(Instant now, RunStats runStats, SnapshotChange change, LocalDate snapshotDate) {
        Objects.requireNonNull(snapshotDate, "snapshotDate");
        move(SyncRunStatus.SUCCEEDED);
        this.finishedAt = now;
        this.stats = runStats;
        this.heldReason = null;
        record((envelopes, version) -> new SyncRunSucceeded(envelopes.wrap(draft("SyncRunSucceeded", version)), syncRunId));
        if (change.hasContent()) {
            record((envelopes, version) -> new OrgSnapshotChanged(envelopes.wrap(draft("OrgSnapshotChanged", version, snapshotDate)),
                    change.changedEmployees(), change.separatedEmployees(), change.changedOrgUnits(), snapshotDate));
        }
    }

    /** From RUNNING, or from HELD when a later run is held for the same reason: the earlier run is refreshed. */
    public void hold(Instant now, RunStats runStats, String reason) {
        hold(now, runStats, reason, Set.of(HoldKind.SHRINK_GUARD), List.of());
    }

    /** A refreshed hold keeps the kinds already lifted only when the kinds and the leavers are exactly the same as before. */
    public void hold(Instant now, RunStats runStats, String reason, Set<HoldKind> kinds, List<UserId> separating) {
        move(SyncRunStatus.HELD);
        this.finishedAt = now;
        this.stats = runStats;
        this.heldReason = reason;
        boolean same = kinds.equals(holdKinds) && Set.copyOf(separating).equals(Set.copyOf(heldSeparations));
        if (!same) {
            this.releasedKinds = Set.of();
        }
        this.holdKinds = Set.copyOf(kinds);
        this.heldSeparations = List.copyOf(separating);
        record((envelopes, version) -> new SyncRunHeld(envelopes.wrap(draft("SyncRunHeld", version)), syncRunId));
    }

    /** Lifts one kind of hold; the run stays held until every kind is lifted. */
    public void releaseKind(HoldKind kind, UserId by) {
        if (status != SyncRunStatus.HELD || !holdKinds.contains(kind) || releasedKinds.contains(kind)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL, ErrorKind.CONFLICT,
                    "the hold " + kind + " of run " + syncRunId.value() + " is not waiting to be lifted");
        }
        Set<HoldKind> lifted = new HashSet<>(releasedKinds);
        lifted.add(kind);
        this.releasedKinds = Set.copyOf(lifted);
        record((envelopes, version) -> new SyncRunHoldReleased(envelopes.wrap(draft("SyncRunHoldReleased", version)), syncRunId, kind, by));
    }

    public void withdrawReleases() {
        this.releasedKinds = Set.of();
    }

    public void fail(Instant now, String reason) {
        move(SyncRunStatus.FAILED);
        this.finishedAt = now;
        this.failureReason = reason;
        record((envelopes, version) -> new SyncRunFailed(envelopes.wrap(draft("SyncRunFailed", version)), syncRunId));
    }

    /** releasedBy is null when the next successful run supersedes the hold. */
    public void release(UserId by, SyncRunId byRun, Instant now, Reason reason) {
        move(SyncRunStatus.RELEASED);
        this.releasedBy = by;
        this.releasedAt = now;
        this.releasedByRunId = Objects.requireNonNull(byRun, "byRun");
        record((envelopes, version) -> new SyncRunReleased(envelopes.wrap(draft("SyncRunReleased", version)), syncRunId, byRun, reason));
    }

    private void move(SyncRunStatus target) {
        this.status = MACHINE.require(status, target, WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL);
    }

    private EventDraft draft(String eventName, long version) {
        return draft(eventName, version, null);
    }

    private EventDraft draft(String eventName, long version, LocalDate businessDate) {
        return new EventDraft("organization.workforce." + eventName, 1, AGGREGATE, String.valueOf(syncRunId.value()), version, null,
                businessDate, new EventSource(SourceSystem.ONE, null), List.of());
    }
}
