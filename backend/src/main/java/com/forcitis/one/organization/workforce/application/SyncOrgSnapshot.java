package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reads the source outside any database transaction; failures never escape: the run is recorded FAILED and the snapshot stays. */
@Service
public class SyncOrgSnapshot {
    private static final Logger LOG = LoggerFactory.getLogger(SyncOrgSnapshot.class);

    private final OrgSource source;
    private final BeginSyncRun begin;
    private final FailSyncRun failRun;
    private final ApplySourceSnapshot apply;
    private final MeterRegistry registry;

    SyncOrgSnapshot(OrgSource source, BeginSyncRun begin, FailSyncRun failRun, ApplySourceSnapshot apply, MeterRegistry registry) {
        this.source = source;
        this.begin = begin;
        this.failRun = failRun;
        this.apply = apply;
        this.registry = registry;
    }

    public SyncResult execute(SyncTrigger trigger, boolean bypassShrinkGuard) {
        SyncRunId run = begin.execute(trigger);
        SyncResult result;
        try {
            SourceOrgSnapshot snapshot = source.readSnapshot();
            result = apply.execute(run, snapshot, bypassShrinkGuard);
        } catch (RuntimeException failure) {
            LOG.error("organization.workforce sync {} failed: {}", run.value(), failure.toString(), failure);
            String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            failRun.execute(run, reason);
            result = new SyncResult(run, SyncRunStatus.FAILED, reason);
        }
        Counter.builder("forcitis.organization.workforce.sync.runs")
                .description("Org snapshot sync runs by result")
                .tag("status", result.status().name())
                .register(registry).increment();
        return result;
    }
}
