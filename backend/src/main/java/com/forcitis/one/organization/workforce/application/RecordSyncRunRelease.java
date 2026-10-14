package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The release and its event commit together; the audit entry is written from the event. */
@Service
class RecordSyncRunRelease {
    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final Clock clock;
    private final AuditLog audit;

    RecordSyncRunRelease(SyncRunRepository runs, DomainEvents events, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.runs = runs;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(SyncRunId held, SyncRunId releasedByRun, UserId actor, String detail) {
        SyncRun run = runs.find(held).orElseThrow();
        run.release(actor, releasedByRun, clock.instant());
        runs.save(run);
        events.publish(run);
        audit.record(new AuditEntry("organization.workforce.sync-run.released", actor, null, "sync-run:" + held.value(), detail));
    }
}
