package com.forcitis.one.platform.idempotency.infrastructure.scheduling;

import com.forcitis.one.platform.idempotency.application.PurgeExpiredResponses;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IdempotencyCleanupJob {
    private final PurgeExpiredResponses purge;

    IdempotencyCleanupJob(PurgeExpiredResponses purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${forcitis.platform.idempotency.cleanup.cron}")
    @SchedulerLock(name = "platform.idempotency.cleanup", lockAtMostFor = "PT10M")
    void removeExpiredAnswers() {
        purge.execute();
    }
}
