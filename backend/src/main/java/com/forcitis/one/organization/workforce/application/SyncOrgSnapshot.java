package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Reads the source outside any database transaction; failures never escape: the run is recorded FAILED and the snapshot stays. */
@Service
public class SyncOrgSnapshot {
    private static final Logger LOG = LoggerFactory.getLogger(SyncOrgSnapshot.class);

    private final OrgSource source;
    private final SyncRunRepository runs;
    private final ApplySourceSnapshot apply;
    private final MeterRegistry registry;
    private final Clock clock;

    SyncOrgSnapshot(OrgSource source, SyncRunRepository runs, ApplySourceSnapshot apply, MeterRegistry registry,
                    ObjectProvider<Clock> clock) {
        this.source = source;
        this.runs = runs;
        this.apply = apply;
        this.registry = registry;
        this.clock = Clocks.from(clock);
    }

    public SyncResult execute(SyncTrigger trigger, boolean bypassShrinkGuard) {
        SyncRunId run = runs.begin(trigger, clock.instant());
        SyncResult result;
        try {
            SourceSnapshot snapshot = source.fetchAll();
            result = apply.execute(run, snapshot, bypassShrinkGuard);
        } catch (RuntimeException failure) {
            LOG.error("organization.workforce sync {} failed: {}", run.value(), failure.toString(), failure);
            String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            runs.markFailed(run, clock.instant(), reason);
            result = new SyncResult(run, SyncRunStatus.FAILED, reason);
        }
        Counter.builder("organization.workforce.sync.runs")
                .description("Org snapshot sync runs by result")
                .tag("status", result.status().name())
                .register(registry).increment();
        return result;
    }
}
