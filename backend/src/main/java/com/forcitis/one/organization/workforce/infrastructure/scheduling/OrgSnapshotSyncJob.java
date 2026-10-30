package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** One node at a time (ShedLock); the lock lasts at most 4 minutes so a hung run never blocks the next cycle for long. */
@Component
@ConditionalOnProperty(prefix = "forcitis.organization.workforce.sync", name = "enabled", havingValue = "true")
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
