package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.HoldKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.Reconciliation;
import com.forcitis.one.organization.workforce.domain.ShrinkGuard;
import com.forcitis.one.organization.workforce.domain.SnapshotReconciler;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Events are published inside the transaction; listeners run after commit, so a rolled-back run announces nothing. */
@Service
public class ApplySourceSnapshot {
    private static final Logger LOG = LoggerFactory.getLogger(ApplySourceSnapshot.class);

    private final OrgSnapshotRepository snapshots;
    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final SeparationReview separations;
    private final EmployeeAccountAccess access;
    private final Clock clock;
    private final BusinessZone businessZone;
    private final int maxDropPercent;

    ApplySourceSnapshot(OrgSnapshotRepository snapshots, SyncRunRepository runs, DomainEvents events, SeparationReview separations,
                        EmployeeAccountAccess access, Clock clock, BusinessZone businessZone,
                        @Value("${forcitis.organization.workforce.sync.max-drop-percent:20}") int maxDropPercent) {
        this.snapshots = snapshots;
        this.runs = runs;
        this.events = events;
        this.separations = separations;
        this.access = access;
        this.clock = clock;
        this.businessZone = businessZone;
        this.maxDropPercent = maxDropPercent;
    }

    @Transactional
    public SyncResult execute(SyncRunId syncRunId, SourceOrgSnapshot source, boolean bypassShrinkGuard) {
        return execute(syncRunId, source, bypassShrinkGuard, null);
    }

    /**
     * releasedBy is the person completing the release of a held run: the kinds of hold already lifted count only if they still
     * stand for what the source says now (ADR-0028), otherwise the run goes back to HELD.
     */
    @Transactional
    SyncResult execute(SyncRunId syncRunId, SourceOrgSnapshot source, boolean bypassShrinkGuard, UserId releasedBy) {
        SyncRun run = runs.find(syncRunId).orElseThrow();
        snapshots.lockForWrite();
        OrgSnapshot current = snapshots.loadAll();
        Reconciliation changes = SnapshotReconciler.reconcile(current, source);
        ShrinkGuard.Verdict verdict = ShrinkGuard.evaluate(changes.stats(), maxDropPercent);
        Instant now = clock.instant();
        LocalDate snapshotDate = businessZone.dateOf(run.startedAt());
        Optional<SyncRun> earlier = runs.findHeldExcept(syncRunId).stream().findFirst();
        Set<HoldKind> lifted = releasedBy == null ? Set.of() : earlier.map(SyncRun::releasedKinds).orElse(Set.of());
        boolean shrinkHeld = verdict.held() && !bypassShrinkGuard && !lifted.contains(HoldKind.SHRINK_GUARD);
        SeparationReview.Verdict review = separations.review(changes.separatedEmployees(), releasedBy);
        boolean separationLifted = lifted.contains(HoldKind.ADMINISTRATOR_SEPARATION) && review.actorRetained()
                && review.remainingAdministrators() >= 1
                && earlier.map(SyncRun::heldSeparations).orElse(List.of()).containsAll(review.separatedAdministrators());
        boolean separationHeld = !review.separatedAdministrators().isEmpty() && !separationLifted;
        if (shrinkHeld || separationHeld) {
            Set<HoldKind> kinds = EnumSet.noneOf(HoldKind.class);
            if (shrinkHeld) {
                kinds.add(HoldKind.SHRINK_GUARD);
            }
            if (separationHeld) {
                kinds.add(HoldKind.ADMINISTRATOR_SEPARATION);
            }
            String reason = Stream.of(shrinkHeld ? verdict.reason() : null, separationHeld ? review.reason() : null)
                    .filter(Objects::nonNull).collect(Collectors.joining("; "));
            List<EmployeeId> leaving = separationHeld
                    ? changes.separatedEmployees().stream().sorted(Comparator.comparing(EmployeeId::value)).toList() : List.of();
            return hold(run, earlier, changes, reason, kinds, leaving, releasedBy != null, now);
        }
        if (verdict.held()) {
            LOG.warn("organization.workforce sync {} runs with the shrink guard bypassed by an administrator: {}",
                    syncRunId.value(), verdict.reason());
        }
        snapshots.save(changes, syncRunId, now, snapshotDate);
        revokeWhereWecomUserIdMoved(current, changes);
        runs.saveDiffs(syncRunId, changes.diffs());
        run.succeed(now, changes.stats(), changes.change(), snapshotDate);
        runs.save(run);
        events.publish(run);
        if (run.trigger() == SyncTrigger.SCHEDULED) {
            for (SyncRun held : runs.findHeldExcept(syncRunId)) {
                held.release(null, syncRunId, now, null);
                runs.save(held);
                events.publish(held);
            }
        }
        return new SyncResult(syncRunId, run.status(), null, List.copyOf(changes.separatedEmployees()));
    }

    /** The WeCom user id is the login binding: an employee whose set id the source moved or cleared loses the sessions made under the old one (#980). */
    private void revokeWhereWecomUserIdMoved(OrgSnapshot current, Reconciliation changes) {
        var before = current.staffById();
        for (var staff : changes.staff()) {
            var was = before.get(staff.employeeId());
            if (was != null && was.employee().wecomUserId() != null && !was.employee().wecomUserId().equals(staff.employee().wecomUserId())) {
                access.revokeSessions(staff.employeeId());
            }
        }
    }

    /** While an earlier run is still held it is refreshed instead, so a stuck hold adds no row every five minutes. */
    private SyncResult hold(SyncRun run, Optional<SyncRun> earlier, Reconciliation changes, String reason, Set<HoldKind> kinds,
                            List<EmployeeId> leaving, boolean releasesWithdrawn, Instant now) {
        SyncRun held = run;
        if (earlier.isPresent()) {
            held = earlier.get();
            runs.delete(run);
        }
        held.hold(now, changes.stats(), reason, kinds, leaving);
        if (releasesWithdrawn) {
            held.withdrawReleases();
        }
        runs.save(held);
        events.publish(held);
        LOG.error("ALERT organization.workforce sync {} held: {}", held.syncRunId().value(), reason);
        return new SyncResult(held.syncRunId(), held.status(), reason, List.of());
    }
}
