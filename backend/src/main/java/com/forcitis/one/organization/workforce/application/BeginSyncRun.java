package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A run killed mid-way (restart, crash) never finishes; the next start closes it so it does not look alive forever. */
@Service
class BeginSyncRun {
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(15);

    private final SyncRunRepository runs;
    private final DomainEvents events;
    private final Clock clock;

    BeginSyncRun(SyncRunRepository runs, DomainEvents events, ObjectProvider<Clock> clock) {
        this.runs = runs;
        this.events = events;
        this.clock = Clocks.from(clock);
    }

    @Transactional
    public SyncRunId execute(SyncTrigger trigger) {
        Instant now = clock.instant();
        for (SyncRun abandoned : runs.findAbandoned(now.minus(ABANDONED_AFTER))) {
            abandoned.fail(now, "interrupted: the run never finished");
            runs.save(abandoned);
            events.publish(abandoned);
        }
        return runs.begin(trigger, now).syncRunId();
    }
}
