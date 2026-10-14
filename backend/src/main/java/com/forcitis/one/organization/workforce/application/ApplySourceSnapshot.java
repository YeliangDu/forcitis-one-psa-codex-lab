package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgSnapshot;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.Reconciliation;
import com.forcitis.one.organization.workforce.domain.ShrinkGuard;
import com.forcitis.one.organization.workforce.domain.SnapshotReconciler;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write transaction of a sync run: take the writer lock, read the current snapshot, compute the
 * differences, apply the shrink guard, write rows + report + run result, and publish the org-change event. The event
 * is published inside the transaction; listeners run after commit, so a rolled-back run announces nothing (RULE-373).
 */
@Service
public class ApplySourceSnapshot {

    private static final Logger LOG = LoggerFactory.getLogger(ApplySourceSnapshot.class);

    private final OrgSnapshotRepository snapshots;
    private final SyncRunRepository runs;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int maxDropPercent;

    ApplySourceSnapshot(OrgSnapshotRepository snapshots, SyncRunRepository runs, ApplicationEventPublisher events,
                        ObjectProvider<Clock> clock,
                        @Value("${forcitis.organization.workforce.sync.max-drop-percent:20}") int maxDropPercent) {
        this.snapshots = snapshots;
        this.runs = runs;
        this.events = events;
        this.clock = Clocks.from(clock);
        this.maxDropPercent = maxDropPercent;
    }

    @Transactional
    public SyncResult execute(SyncRunId syncRunId, SourceSnapshot source, boolean bypassShrinkGuard) {
        snapshots.lockForWrite();
        OrgSnapshot current = snapshots.loadAll();
        Reconciliation changes = SnapshotReconciler.reconcile(current, source);
        ShrinkGuard.Verdict verdict = ShrinkGuard.evaluate(changes.stats(), maxDropPercent);
        Instant now = clock.instant();
        if (verdict.held() && !bypassShrinkGuard) {
            SyncRunId held = runs.markHeld(syncRunId, now, changes.stats(), verdict.reason());
            LOG.error("ALERT organization.workforce sync {} held by the shrink guard: {}", held.value(), verdict.reason());
            return new SyncResult(held, SyncRunStatus.HELD, verdict.reason());
        }
        if (verdict.held()) {
            LOG.warn("organization.workforce sync {} runs with the shrink guard bypassed by an administrator: {}",
                    syncRunId.value(), verdict.reason());
        }
        snapshots.save(changes, syncRunId, now);
        runs.saveDiffs(syncRunId, changes.diffs());
        runs.markSucceeded(syncRunId, now, changes.stats());
        if (changes.hasEventContent()) {
            events.publishEvent(new OrgSnapshotChanged(changes.changedEmployees(), changes.separatedEmployees(),
                    changes.changedOrgUnits()));
        }
        return new SyncResult(syncRunId, SyncRunStatus.SUCCEEDED, null);
    }
}
