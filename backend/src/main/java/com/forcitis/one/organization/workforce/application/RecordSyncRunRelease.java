package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks the held run released and audits who did it, in one transaction: no audit entry without the release, and vice versa. */
@Service
class RecordSyncRunRelease {

    private final SyncRunRepository runs;
    private final ObjectProvider<AuditLog> audit;
    private final Clock clock;

    RecordSyncRunRelease(SyncRunRepository runs, ObjectProvider<AuditLog> audit, ObjectProvider<Clock> clock) {
        this.runs = runs;
        this.audit = audit;
        this.clock = Clocks.from(clock);
    }

    @Transactional
    public void execute(SyncRunId held, SyncRunId releasedByRun, UserId actor) {
        Instant now = clock.instant();
        runs.markReleased(held, releasedByRun, actor, now);
        audit.getObject().record(new AuditEntry("organization.workforce.sync-run.released", actor, null,
                "sync-run:" + held.value(), "held run released; executed as sync run " + releasedByRun.value()));
    }
}
