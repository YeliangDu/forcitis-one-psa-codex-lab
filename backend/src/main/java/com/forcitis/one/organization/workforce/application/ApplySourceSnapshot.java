package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.Reconciliation;
import com.forcitis.one.organization.workforce.domain.ShrinkGuard;
import com.forcitis.one.organization.workforce.domain.SnapshotReconciler;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private final Clock clock;
    private final BusinessZone businessZone;
    private final int maxDropPercent;

    ApplySourceSnapshot(OrgSnapshotRepository snapshots, SyncRunRepository runs, DomainEvents events,
                        Clock clock, BusinessZone businessZone,
                        @Value("${forcitis.organization.workforce.sync.max-drop-percent:20}") int maxDropPercent) {
        this.snapshots = snapshots;
        this.runs = runs;
        this.events = events;
        this.clock = clock;
        this.businessZone = businessZone;
        this.maxDropPercent = maxDropPercent;
    }

    @Transactional
    public SyncResult execute(SyncRunId syncRunId, SourceOrgSnapshot source, boolean bypassShrinkGuard) {
        SyncRun run = runs.find(syncRunId).orElseThrow();
        snapshots.lockForWrite();
        OrgSnapshot current = snapshots.loadAll();
        Reconciliation changes = SnapshotReconciler.reconcile(current, source);
        ShrinkGuard.Verdict verdict = ShrinkGuard.evaluate(changes.stats(), maxDropPercent);
        Instant now = clock.instant();
        if (verdict.held() && !bypassShrinkGuard) {
            return hold(run, changes, verdict.reason(), now);
        }
        if (verdict.held()) {
            LOG.warn("organization.workforce sync {} runs with the shrink guard bypassed by an administrator: {}",
                    syncRunId.value(), verdict.reason());
        }
        snapshots.save(changes, syncRunId, now);
        runs.saveDiffs(syncRunId, changes.diffs());
        run.succeed(now, changes.stats(), changes.change(), businessZone.dateOf(run.startedAt()));
        runs.save(run);
        events.publish(run);
        if (run.trigger() == SyncTrigger.SCHEDULED) {
            for (SyncRun held : runs.findHeldExcept(syncRunId)) {
                held.release(null, syncRunId, now);
                runs.save(held);
                events.publish(held);
            }
        }
        return new SyncResult(syncRunId, run.status(), null);
    }

    /** While an earlier run is still held it is refreshed instead, so a stuck hold adds no row every five minutes. */
    private SyncResult hold(SyncRun run, Reconciliation changes, String reason, Instant now) {
        List<SyncRun> earlier = runs.findHeldExcept(run.syncRunId());
        SyncRun held = run;
        if (!earlier.isEmpty()) {
            held = earlier.getFirst();
            runs.delete(run);
        }
        held.hold(now, changes.stats(), reason);
        runs.save(held);
        events.publish(held);
        LOG.error("ALERT organization.workforce sync {} held by the shrink guard: {}", held.syncRunId().value(), reason);
        return new SyncResult(held.syncRunId(), held.status(), reason);
    }
}
