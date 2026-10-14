package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The release and its event commit together; the audit entry is written from the event. */
@Service
class RecordSyncRunRelease {
    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final Clock clock;

    RecordSyncRunRelease(SyncRunRepository runs, DomainEvents events, Clock clock) {
        this.runs = runs;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(SyncRunId held, SyncRunId releasedByRun, UserId actor, Reason reason) {
        SyncRun run = runs.find(held).orElseThrow();
        run.release(actor, releasedByRun, clock.instant(), reason);
        runs.save(run);
        events.publish(run);
    }
}
