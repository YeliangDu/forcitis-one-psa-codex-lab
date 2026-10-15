package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.OrgSnapshotStatus;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stale is the sync lag the operations alert OneWorkforceSyncLagging fires on: no successful run for longer than staleAfter. */
@Component
class OrgSnapshotStatusService implements OrgSnapshotStatus {

    private final SyncRunQueries runs;
    private final Clock clock;
    private final Duration staleAfter;

    OrgSnapshotStatusService(SyncRunQueries runs, Clock clock, @Value("${forcitis.organization.workforce.sync.stale-after}") Duration staleAfter) {
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("forcitis.organization.workforce.sync.stale-after must be positive");
        }
        this.runs = runs;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrent() {
        return runs.findLastSucceededAt().map(succeeded -> !succeeded.plus(staleAfter).isBefore(clock.instant())).orElse(false);
    }
}
