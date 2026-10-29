package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.EventDraft;
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
import org.jspecify.annotations.Nullable;

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

    private final @Nullable SyncRunId syncRunId;
    private final SyncTrigger trigger;
    private final Instant startedAt;
    private final SyncRunStatus loadedStatus;
    private SyncRunStatus status;
    private @Nullable Instant finishedAt;
    private @Nullable RunStats stats;
    private @Nullable String heldReason;
    private @Nullable String failureReason;
    private @Nullable UserId releasedBy;
    private @Nullable Instant releasedAt;
    private @Nullable SyncRunId releasedByRunId;
    private Set<HoldKind> holdKinds;
    private Set<HoldKind> releasedKinds;
    private List<EmployeeId> heldSeparations;

    private SyncRun(@Nullable SyncRunId syncRunId, SyncTrigger trigger, SyncRunStatus status, Instant startedAt, @Nullable Instant finishedAt,
                    @Nullable RunStats stats, @Nullable String heldReason, @Nullable String failureReason, @Nullable UserId releasedBy, @Nullable Instant releasedAt,
                    @Nullable SyncRunId releasedByRunId, Set<HoldKind> holdKinds, Set<HoldKind> releasedKinds, List<EmployeeId> heldSeparations, long version,
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
    public static SyncRun restore(@Nullable SyncRunId syncRunId, SyncTrigger trigger, String status, Instant startedAt, @Nullable Instant finishedAt,
                                  @Nullable RunStats stats, @Nullable String heldReason, @Nullable String failureReason, @Nullable UserId releasedBy, @Nullable Instant releasedAt,
                                  @Nullable SyncRunId releasedByRunId, long version, long eventSequence) {
        return restore(syncRunId, trigger, status, startedAt, finishedAt, stats, heldReason, failureReason, releasedBy, releasedAt,
                releasedByRunId, heldReason == null ? Set.of() : Set.of(HoldKind.SHRINK_GUARD), Set.of(), List.of(), version, eventSequence);
    }

    public static SyncRun restore(@Nullable SyncRunId syncRunId, SyncTrigger trigger, String status, Instant startedAt, @Nullable Instant finishedAt,
                                  @Nullable RunStats stats, @Nullable String heldReason, @Nullable String failureReason, @Nullable UserId releasedBy, @Nullable Instant releasedAt,
                                  @Nullable SyncRunId releasedByRunId, Set<HoldKind> holdKinds, Set<HoldKind> releasedKinds,
                                  List<EmployeeId> heldSeparations, long version, long eventSequence) {
        return new SyncRun(syncRunId, trigger, SyncRunStatus.valueOf(status), startedAt, finishedAt, stats, heldReason,
                failureReason, releasedBy, releasedAt, releasedByRunId, holdKinds, releasedKinds, heldSeparations, version, eventSequence);
    }

    public Set<HoldKind> holdKinds() {
        return holdKinds;
    }

    public Set<HoldKind> releasedKinds() {
        return releasedKinds;
    }

    public List<EmployeeId> heldSeparations() {
        return heldSeparations;
    }

    public boolean allHoldsReleased() {
        return releasedKinds.containsAll(holdKinds);
    }

    public @Nullable SyncRunId syncRunId() {
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

    public @Nullable Instant finishedAt() {
        return finishedAt;
    }

    public @Nullable RunStats stats() {
        return stats;
    }

    public @Nullable String heldReason() {
        return heldReason;
    }

    public @Nullable String failureReason() {
        return failureReason;
    }

    public @Nullable UserId releasedBy() {
        return releasedBy;
    }

    public @Nullable Instant releasedAt() {
        return releasedAt;
    }

    public @Nullable SyncRunId releasedByRunId() {
        return releasedByRunId;
    }

    /** snapshotDate is the business day on which the source was read; the source gives no earlier date for what changed. */
    public void succeed(Instant now, RunStats runStats, SnapshotChange change, LocalDate snapshotDate) {
        Objects.requireNonNull(snapshotDate, "snapshotDate");
        move(SyncRunStatus.SUCCEEDED);
        this.finishedAt = now;
        this.stats = runStats;
        this.heldReason = null;
        record((envelopes, version) -> new SyncRunSucceeded(envelopes.wrap(draftWithSnapshot("SyncRunSucceeded", version, null)), syncRunId));
        if (change.hasContent()) {
            record((envelopes, version) -> new OrgSnapshotChanged(envelopes.wrap(draftWithSnapshot("OrgSnapshotChanged", version, snapshotDate)),
                    change.changedEmployees(), change.separatedEmployees(), change.changedOrgUnits(), snapshotDate));
        }
    }

    /** From RUNNING, or from HELD when a later run is held for the same reason: the earlier run is refreshed. */
    public void hold(Instant now, RunStats runStats, String reason) {
        hold(now, runStats, reason, Set.of(HoldKind.SHRINK_GUARD), List.of());
    }

    /** A refreshed hold keeps the kinds already lifted only when the kinds and the leavers are exactly the same as before. */
    public void hold(Instant now, RunStats runStats, String reason, Set<HoldKind> kinds, List<EmployeeId> separating) {
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
                    "the hold " + kind + " of run " + Objects.requireNonNull(syncRunId, "syncRunId is null until the repository assigns it with withId").value() + " is not waiting to be lifted");
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
    public void release(@Nullable UserId by, SyncRunId byRun, Instant now, Reason reason) {
        move(SyncRunStatus.RELEASED);
        this.releasedBy = by;
        this.releasedAt = now;
        this.releasedByRunId = Objects.requireNonNull(byRun, "byRun");
        record((envelopes, version) -> new SyncRunReleased(envelopes.wrap(draft("SyncRunReleased", version)), syncRunId, byRun, reason));
    }

    private void move(SyncRunStatus target) {
        this.status = MACHINE.require(status, target, WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL);
    }

    /** A successful run applies the snapshot in the same transaction (ApplySourceSnapshot, registered in multi-aggregate-exceptions.txt); its events say so. */
    private EventDraft draftWithSnapshot(String eventName, long version, @Nullable LocalDate businessDate) {
        return new EventDraft("organization.workforce." + eventName, 1, AGGREGATE, Objects.requireNonNull(syncRunId, "syncRunId is null until the repository assigns it with withId").value(), version, null, businessDate, List.of(new AggregateReference("OrgSnapshot", Objects.requireNonNull(syncRunId, "syncRunId is null until the repository assigns it with withId").value())));
    }

    private EventDraft draft(String eventName, long version) {
        return draft(eventName, version, null);
    }

    private EventDraft draft(String eventName, long version, @Nullable LocalDate businessDate) {
        return new EventDraft("organization.workforce." + eventName, 1, AGGREGATE, Objects.requireNonNull(syncRunId, "syncRunId is null until the repository assigns it with withId").value(), version, null,
                businessDate, List.of());
    }
}
