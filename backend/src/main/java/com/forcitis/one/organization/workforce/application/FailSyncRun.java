package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FailSyncRun {
    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final Clock clock;

    FailSyncRun(SyncRunRepository runs, DomainEvents events, Clock clock) {
        this.runs = runs;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public SyncRunStatus execute(SyncRunId syncRunId, String reason) {
        SyncRun run = runs.find(syncRunId).orElseThrow();
        run.fail(clock.instant(), reason);
        runs.save(run);
        events.publish(run);
        return run.status();
    }
}
