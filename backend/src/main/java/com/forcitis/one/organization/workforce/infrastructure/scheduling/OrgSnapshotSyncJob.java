package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls the A8 org snapshot every five minutes (REIMAGINED_ARCHITECTURE §3.3). One node at a time (ShedLock); the lock
 * lasts at most 4 minutes so a hung run never blocks the next cycle for long. Present only when A8 access is enabled.
 * Job / lock / metric names are {subdomain}.{bc}.{name}; configuration lives under forcitis.organization.workforce (G46).
 */
@Component
@ConditionalOnProperty(prefix = "forcitis.organization.workforce.a8", name = "enabled", havingValue = "true")
class OrgSnapshotSyncJob {

    private final SyncOrgSnapshot sync;

    OrgSnapshotSyncJob(SyncOrgSnapshot sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${forcitis.organization.workforce.sync.cron}")
    @SchedulerLock(name = "organization.workforce.snapshot.sync", lockAtMostFor = "PT4M")
    void synchronize() {
        sync.execute(SyncTrigger.SCHEDULED, false);
    }
}
